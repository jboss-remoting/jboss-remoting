package org.jboss.remoting.core.security.sasl;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.jboss.remoting.util.Base64DecodingException;
import org.jboss.remoting.util.CollectionUtil;
import org.jboss.xnio.log.Logger;

import javax.crypto.NoSuchPaddingException;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

/**
 *
 */
public final class SrpSaslServerImpl extends AbstractSrpSaslParticipant implements SaslServer {

    private static final Logger log = Logger.getLogger(SrpSaslServerImpl.class);

    private String authenticationId;
    private String authorizationId;
    private BigInteger B;
    private BigInteger b;
    private SrpVerifier verifier;
    private MessageDigest digest;
    private String digestName;

    // todo - move to base class
    private final boolean requireReplay;
    private final boolean requireIntegrity;
    private final boolean requireConfidentiality;

    private static final byte[] noReuseSessionFlag = new byte[] { 0 };
    private String serverOptionString;

    protected SrpSaslServerImpl(final CallbackHandler callbackHandler, final Map<String, ?> props) {
        super(callbackHandler);
        boolean requireReplay = false;
        boolean requireIntegrity = false;
        boolean requireConfidentiality = false;
        if (props.containsKey(Sasl.QOP)) {
            for (String qop : CollectionUtil.split(",", (String) props.get(Sasl.QOP))) {
                if ("auth".equals(qop)) {
                    requireIntegrity = false;
                    requireConfidentiality = false;
                    break;
                } else if ("auth-int".equals(qop)) {
                    requireReplay = true;
                    requireIntegrity = true;
                    break;
                } else if ("auth-int-noreplay".equals(qop)) {
                    requireIntegrity = true;
                    break;
                } else if ("auth-conf".equals(qop)) {
                    requireReplay = true;
                    requireIntegrity = true;
                    requireConfidentiality = true;
                    break;
                } else if ("auth-conf-noreplay".equals(qop)) {
                    requireIntegrity = true;
                    requireConfidentiality = true;
                    break;
                } else {
                    // unsupported; loop next
                }
            }
        }
        this.requireReplay = requireReplay;
        this.requireIntegrity = requireIntegrity;
        this.requireConfidentiality = requireConfidentiality;
        if (props.containsKey(Srp.VERIFIER_MODE)) {
            final Object verifierModeString = props.get(Srp.VERIFIER_MODE);
            if ("password".equals(verifierModeString)) {
                verifierMode = VerifierMode.PASSWORD;
            } else if ("callback".equals(verifierModeString)) {
                verifierMode = VerifierMode.CALLBACK;
            } else if ("encoded".equals(verifierModeString)) {
                verifierMode = VerifierMode.ENCODED;
            }
        }
    }

    private enum State {
        FAILED,
        INVALID,
        INITIAL,
        RECEIVED_IDENTITY,
        RECEIVED_KEY_AND_EVIDENCE,
        COMPLETE,
    }

    private enum VerifierMode {
        PASSWORD,
        CALLBACK,
        ENCODED,
    }

    private State state = State.INITIAL;
    private VerifierMode verifierMode = VerifierMode.PASSWORD;

    public byte[] evaluateResponse(byte[] response) throws SaslException {
        switch (state) {
            case INITIAL:
                try {
                    if (response.length == 0) {
                        return new byte[0];
                    }
                    final byte[] bytes = receiveIdentity(response);
                    state = State.RECEIVED_IDENTITY;
                    return bytes;
                } catch (SaslException ex) {
                    state = State.FAILED;
                    throw ex;
                }
            case RECEIVED_IDENTITY:
                try {
                    final byte[] bytes = receiveKeyAndEvidence(response);
                    state = State.RECEIVED_KEY_AND_EVIDENCE;
                    return bytes;
                } catch (SaslException ex) {
                    state = State.FAILED;
                    throw ex;
                }
            case RECEIVED_KEY_AND_EVIDENCE:
                state = State.COMPLETE;
                return null;
            case COMPLETE:
                throw new SaslException("Received SRP response after negotiation was already complete");
            case FAILED:
                throw new SaslException("SRP negotiation failed previously");
            case INVALID:
                throw new SaslException("SRP session has been invalidated");
            default:
                throw new IllegalStateException("Illegal state in SRP SASL state machine");
        }
    }

    private void runCallbacks(CallbackHandler handler, NameCallback nameCallback, Callback otherCallback) throws UnsupportedCallbackException, IOException {
        handler.handle(new Callback[] { nameCallback, otherCallback });
        authenticationId = nameCallback.getName();
        if (authenticationId == null || authenticationId.length() == 0) {
            throw new SaslException("Invalid authentication ID provided");
        }
    }

    private byte[] receiveIdentity(final byte[] response) throws SaslException {
        final ByteBuffer responseBuffer = ByteBuffer.wrap(response);
        if (readInteger(responseBuffer) != responseBuffer.remaining()) {
            throw new SaslException("Buffer from client has incorrect size");
        }
        authenticationId = readUtf8(responseBuffer);
        authorizationId = readUtf8(responseBuffer);

        final SrpOptions serverOptions = new SrpOptions();

        final Random rng = getRandom();
        try {
            final NameCallback nameCallback = authenticationId == null || authenticationId.length() == 0 ? new NameCallback(namePrompt) : new NameCallback(namePrompt, authenticationId);

            final PasswordCallback passwordCallback;
            final char[] password;

            switch (verifierMode) {
                case PASSWORD:
                    // The password was given; we derive N, s, g, and v from it
                    // We'll pick sha-160 for now
                    passwordCallback = new PasswordCallback(passwordPrompt, false);
                    runCallbacks(callbackHandler, nameCallback, passwordCallback);
                    password = passwordCallback.getPassword();
                    if (password == null) {
                        throw new SaslException("No password found for user \"" + authenticationId + "\"");
                    }
                    digestName = "sha-256";
                    serverOptions.getMdaSet().add(digestName);
                    digest = MessageDigest.getInstance("SHA-256");
                    verifier = SrpVerifier.generate(password, 768, authenticationId, digestName, digest, utf8Encoder, rng);
                    break;
                case CALLBACK:
                    // We must use the callback to identify the message digest and get N, s, g, and v for a user
                    final SrpVerifierCallback verifierCallback = new SrpVerifierCallback();
                    runCallbacks(callbackHandler, nameCallback, verifierCallback);
                    verifier = verifierCallback.getVerifier();
                    if (verifier == null) {
                        throw new SaslException("No verifier found for user \"" + authenticationId + "\"");
                    }
                    digestName = verifier.getMessageDigestName();
                    if (! SRP_TO_JCA_MD.containsKey(digestName)) {
                        throw new SaslException("Verifier uses unknown digest type \"" + digestName + "\"");
                    }
                    digest = MessageDigest.getInstance(SRP_TO_JCA_MD.get(digestName));
                    break;
                case ENCODED:
                    // We must use data encoded in the password to identify the message digest and get N, s, g, and v for a user
                    passwordCallback = new PasswordCallback(passwordPrompt, false);
                    runCallbacks(callbackHandler, nameCallback, passwordCallback);
                    password = passwordCallback.getPassword();
                    if (password == null) {
                        throw new SaslException("No password found for user \"" + authenticationId + "\"");
                    }
                    verifier = SrpVerifier.readEncoded(CharBuffer.wrap(password));
                    digestName = verifier.getMessageDigestName();
                    if (! SRP_TO_JCA_MD.containsKey(digestName)) {
                        throw new SaslException("Verifier uses unknown digest type \"" + digestName + "\"");
                    }
                    digest = MessageDigest.getInstance(SRP_TO_JCA_MD.get(digestName));
                    break;
            }
        } catch (SaslException e) {
            // Catch this first, because SaslException extends IOException
            throw e;
        } catch (IOException e) {
            throw new SaslException("Authentication failed (I/O exception: " + e.getMessage() + ")", e);
        } catch (UnsupportedCallbackException e) {
            throw new SaslException("Authentication failed (callback unsupported: " + e.getMessage() + ")", e);
        } catch (NoSuchAlgorithmException e) {
            throw new SaslException("Authentication failed (no such algorithm: " + e.getMessage() + ")", e);
        } catch (Base64DecodingException e) {
            throw new SaslException("Authentication failed (Base64 decode failed: " + e.getMessage() + ")", e);
        }

        final BigInteger N = verifier.getSafePrime();
        final BigInteger g = new BigInteger(Integer.toString(verifier.getGenerator()));
        final BigInteger v = verifier.getVerifier();

        // I'm thinking of a number between zero and a hundred bazillion...
        byte[] b_bytes = new byte[64];
        rng.nextBytes(b_bytes);
        b = new BigInteger(1, b_bytes);
        // B = (3v + g^b) % N, thus B = (3v + g^b % N) % N
        B = THREE.multiply(v).add(g.modPow(b, N)).mod(N);

        final byte[] salt = unsignedByteArrayFor(verifier.getSalt());

        serverOptions.getConfidentialitySet().add("aes");
        serverOptions.getConfidentialitySet().add("blowfish");
        serverOptions.setConfidentialityManditory(requireConfidentiality);
        serverOptions.setIntegrityManditory(requireIntegrity);
        serverOptions.setReplayDetectionManditory(requireReplay);
        serverOptions.setReplayDetection(true);
        final ByteBuffer challengeBuffer = createSrpBuffer();
        writeOctetSeq(challengeBuffer, noReuseSessionFlag);
        writeMpi(challengeBuffer, N);
        writeMpi(challengeBuffer, g);
        writeOctetSeq(challengeBuffer, salt);
        writeMpi(challengeBuffer, B);
        serverOptionString = serverOptions.toString();
        writeUtf8(challengeBuffer, serverOptionString);
        return getSrpBytes(challengeBuffer);
    }

    private byte[] receiveKeyAndEvidence(final byte[] response) throws SaslException {
        final ByteBuffer responseBuffer = ByteBuffer.wrap(response);
        // sid and ttl are unused because we do not support resuming a session
        final int ttl = 0;
        final String sid = "";
        readInteger(responseBuffer); // length
        BigInteger A = readMpi(responseBuffer);
        byte[] client_M1_bytes = readOctetSeq(responseBuffer);
        String clientOptionString = readUtf8(responseBuffer);
        SrpOptions clientOptions = new SrpOptions(clientOptionString);
        byte[] cIV_bytes = readOctetSeq(responseBuffer);

        final MessageDigest digest = this.digest;
        final Set<String> mdaSet;
        if (clientOptions.getMdaSet().isEmpty()) {
            mdaSet = Collections.singleton("sha-160");
        } else if (clientOptions.getMdaSet().size() > 1) {
            throw new SaslException("Client specified more than one message digest");
        } else {
            mdaSet = clientOptions.getMdaSet();
        }
        if (! mdaSet.contains(digestName)) {
            throw new SaslException("Client did not agree on the message digest \"" + digestName + "\"");
        }

        final byte[] A_bytes = unsignedByteArrayFor(A);
        digest.update(A_bytes);
        digest.update(unsignedByteArrayFor(B));
        final byte[] u_bytes = digest.digest();
        final BigInteger u = new BigInteger(1, u_bytes);
        final BigInteger v = verifier.getVerifier();
        final BigInteger N = verifier.getSafePrime();
        final BigInteger S = A.multiply(v.modPow(u, N)).mod(N).modPow(b, N);
        final byte[] I_digest = digest.digest(bytesOf(authorizationId));
        final byte[] o_digest = digest.digest(bytesOf(clientOptionString));
        final byte[] K_bytes = digest.digest(unsignedByteArrayFor(S));
        digest.update(A_bytes);
        digest.update(client_M1_bytes);
        digest.update(K_bytes);
        digest.update(I_digest);
        digest.update(o_digest);
        digest.update(bytesOf(sid));
        digest.update((ByteBuffer) ByteBuffer.allocate(4).putInt(ttl).flip());
        final byte[] M2_bytes = digest.digest();
        // Now verify client's evidence M1
        byte[] N_digest = digest.digest(unsignedByteArrayFor(N));
        byte[] g_digest = digest.digest(unsignedByteArrayFor(new BigInteger(Integer.toString(verifier.getGenerator()))));
        byte[] U_digest = digest.digest(bytesOf(authenticationId));
        byte[] L_digest = digest.digest(bytesOf(serverOptionString));
        digest.update(bitXor(N_digest, g_digest));
        digest.update(U_digest);
        digest.update(unsignedByteArrayFor(verifier.getSalt()));
        digest.update(A_bytes);
        digest.update(unsignedByteArrayFor(B));
        digest.update(K_bytes);
        digest.update(I_digest);
        digest.update(L_digest);
        byte[] server_M1_bytes = digest.digest();

        if (! Arrays.equals(client_M1_bytes, server_M1_bytes)) {
            throw new SaslException("Client authentication failed");
        }
        final byte[] sIV_bytes = new byte[64];
        getRandom().nextBytes(sIV_bytes);

        final Set<String> clientConfidentialitySet = clientOptions.getConfidentialitySet();
        if (clientConfidentialitySet.size() > 1) {
            throw new SaslException("Client selected more than one confidentiality method");
        }
        if (clientOptions.getIntegritySet().size() > 1) {
            throw new SaslException("Client selected more than one integrity method");
        }
        if (clientOptions.getMdaSet().size() > 1) {
            throw new SaslException("Client selected more than one message digest method");
        }
        if (! clientConfidentialitySet.isEmpty()) {
            try {
                selectConfidentiality(clientConfidentialitySet.iterator().next());
                enableConfidentiality(K_bytes, cIV_bytes, sIV_bytes);
            } catch (NoSuchAlgorithmException e) {
                throw new SaslException("Client's confidentiality algorithm cannot be supported", e);
            } catch (InvalidKeyException e) {
                throw new SaslException("Client's confidentiality algorithm can not support the session key", e);
            } catch (InvalidAlgorithmParameterException e) {
                throw new SaslException("Client's confidentiality algorithm has an invalid parameter", e);
            } catch (NoSuchPaddingException e) {
                throw new SaslException("Client's confidentiality algorithm can not support the required padding", e);
            }
        }

        if (requireConfidentiality && ! isConfidentialityEnabled()) {
            throw new SaslException("Client refused to enable confidentiality");
        }
        if (requireIntegrity && ! isIntegrityEnabled()) {
            throw new SaslException("Client refused to enable integrity");
        }
        if (requireReplay && ! isReplayEnabled()) {
            throw new SaslException("Client refused to enable replay protection");
        }

        final AuthorizeCallback authorizeCallback = new AuthorizeCallback(authenticationId, authorizationId);
        try {
            callbackHandler.handle(new Callback[] { authorizeCallback });
        } catch (SaslException e) {
            throw e;
        } catch (IOException e) {
            throw new SaslException("Authorization failed (I/O exception: " + e.getMessage() + ")", e);
        } catch (UnsupportedCallbackException e) {
            throw new SaslException("Authorization failed (callback unsupported: " + e.getMessage() + ")", e);
        }
        authorizationId = authorizeCallback.getAuthorizedID();
        if (authorizationId == null || ! authorizeCallback.isAuthorized()) {
            throw new SaslException("Authorization failed");
        }

        final ByteBuffer challengeBuffer = createSrpBuffer();
        writeOctetSeq(challengeBuffer, M2_bytes);
        writeOctetSeq(challengeBuffer, sIV_bytes);
        writeUtf8(challengeBuffer, sid);
        challengeBuffer.putInt(ttl);
        return getSrpBytes(challengeBuffer);
    }

    public boolean isComplete() {
        return state == State.COMPLETE;
    }

    public String getAuthorizationID() {
        if (isComplete()) {
            return authorizationId;
        } else {
            throw new IllegalStateException("Authentication not completed");
        }
    }

    public void dispose() throws SaslException {
        state = State.INVALID;
        verifier = null;
    }
}
