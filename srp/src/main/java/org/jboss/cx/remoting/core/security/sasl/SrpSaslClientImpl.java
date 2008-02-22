package org.jboss.cx.remoting.core.security.sasl;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.jboss.cx.remoting.util.CollectionUtil;
import org.jboss.cx.remoting.log.Logger;

import javax.crypto.NoSuchPaddingException;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

/**
 *
 */
public final class SrpSaslClientImpl extends AbstractSrpSaslParticipant implements SaslClient {
    private byte[] cIV_bytes;
    private byte[] K_bytes;
    private MessageDigest digest;
    private byte[] A_bytes;
    private byte[] M1_bytes;
    private byte[] I_digest;
    private String clientOptionString;

    private enum State {
        FAILED,
        INVALID,
        INITIAL,
        SENT_IDENTITY,
        SENT_KEY_AND_EVIDENCE,
        COMPLETE,
    }

    private State state = State.INITIAL;

    private String authorizationId;
    private String authenticationId;
    private char[] password;

    private final boolean requireReplay;
    private final boolean requireIntegrity;
    private final boolean requireConfidentiality;

    private static final Logger log = Logger.getLogger(SrpSaslClientImpl.class);

    public SrpSaslClientImpl(final String authorizationId, final CallbackHandler callbackHandler, final Map<String, ?> props) throws SaslException {
        super(callbackHandler);
        this.authorizationId = authorizationId;
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
    }

    public boolean hasInitialResponse() {
        return true;
    }

    public byte[] evaluateChallenge(byte[] challenge) throws SaslException {
        switch (state) {
            case INITIAL:
                try {
                    final byte[] bytes = sendIdentity();
                    state = State.SENT_IDENTITY;
                    return bytes;
                } catch (SaslException ex) {
                    state = State.FAILED;
                    throw ex;
                }
            case SENT_IDENTITY:
                try {
                    final byte[] bytes = sendKeyAndEvidence(challenge);
                    state = State.SENT_KEY_AND_EVIDENCE;
                    return bytes;
                } catch (SaslException ex) {
                    state = State.FAILED;
                    throw ex;
                }
            case SENT_KEY_AND_EVIDENCE:
                try {
                    complete(challenge);
                    state = State.COMPLETE;
                    return new byte[0];
                } catch (SaslException ex) {
                    state = State.FAILED;
                    throw ex;
                }
            case COMPLETE:
                if (challenge.length == 0) {
                    return new byte[0];
                }
                throw new SaslException("Received SRP challenge after negotiation was already complete");
            case FAILED:
                throw new SaslException("SRP negotiation failed previously");
            case INVALID:
                throw new SaslException("SRP session has been invalidated");
            default:
                throw new IllegalStateException("Illegal state in SRP SASL state machine");
        }
    }

    private byte[] sendIdentity() throws SaslException {
        final ByteBuffer buffer;
        try {
            final NameCallback nameCallback = authorizationId == null || authorizationId.length() == 0 ? new NameCallback(namePrompt) : new NameCallback(namePrompt, authorizationId);
            final PasswordCallback passwordCallback = new PasswordCallback(passwordPrompt, false);
            callbackHandler.handle(new Callback[] { nameCallback, passwordCallback });
            authenticationId = nameCallback.getName();
            if (authenticationId == null || authenticationId.length() == 0) {
                throw new SaslException("Callback handler provided an empty value for SRP authentication ID");
            }
            password = passwordCallback.getPassword();
            if (password == null || password.length == 0) {
                throw new SaslException("Callback handler provided an empty value for SRP password");
            }
        } catch (Exception e) {
            throw new SaslException("Failed to handle callbacks for SRP: " + e.getMessage(), e);
        }
        if (authorizationId == null) {
            authorizationId = "";
        }
        if (authenticationId == null) {
            authenticationId = "";
        }
        buffer = createSrpBuffer();
        writeUtf8(buffer, authenticationId);
        writeUtf8(buffer, authorizationId);
        return getSrpBytes(buffer);
    }

    /**
     *
     * Negotiation Rules:
     *
     * * server MUST support SHA-160 message digest, even if not advertised
     * * server MUST advertise at least one integrity algorithm
     * * server SHOULD advertise HMAC-SHA-160 for integrity
     * * client SHOULD always select integrity protection, even if server does not advertise it
     * * server MAY advertise reply_detection
     * * replay_detection always implies integrity protection, hmac-sha-160 if none given by client
     * * server SHOULD advertise confidentiality
     * * if server supports confidentiality, it MUST advertise AES
     * * server MAY specify mandatory integrity; if client does not select integrity, server MUST abort the connection
     * * server MAY specify mandatory replay detection; if client does not select replay detection, server MUST abort the connection
     * * server MAY specify mandatory confidentiality; if client does not select confidentiality, server MUST abort the connection
     * * server SHOULD always specify mandatory confidentiality, unless the channel is physically secure
     * * client SHOULD abort connection if the server does not provide an option it requires
     * 
     *
     *
     *
     * @param challenge
     * @return
     * @throws SaslException
     */
    private byte[] sendKeyAndEvidence(final byte[] challenge) throws SaslException {
        final boolean trace = log.isTrace();
        final ByteBuffer challengeBuffer;
        final ByteBuffer buffer;
        challengeBuffer = ByteBuffer.wrap(challenge);
        if (readInteger(challengeBuffer) != challengeBuffer.remaining()) {
            throw new SaslException("Buffer from server has incorrect size");
        }
        readOctetSeq(challengeBuffer); // todo check for 00
        BigInteger N = readMpi(challengeBuffer);
        BigInteger g = readMpi(challengeBuffer);
        byte[] s_bytes = readOctetSeq(challengeBuffer);
        BigInteger B = readMpi(challengeBuffer);
        String serverOptionString = readUtf8(challengeBuffer);

        if (B.mod(N).signum() == 0) {
            throw new SaslException("Server protocol elements are invalid (server public key % modulus == 0)");
        }

        SrpOptions serverOptions = new SrpOptions(serverOptionString);
        SrpOptions clientOptions = new SrpOptions();

        // Options negotiation
        // First, negotiate a message digest algorithm
        String selectedMda = null;
        for (String mda : serverOptions.getMdaSet()) {
            String jcaName = SRP_TO_JCA_MD.containsKey(mda) ? SRP_TO_JCA_MD.get(mda) : mda.toUpperCase();
            try {
                digest = MessageDigest.getInstance(jcaName);
            } catch (NoSuchAlgorithmException e) {
                if (trace) log.trace("Rejected JCA message digest algorithm '" + jcaName + "', SRP name '" + mda + "': " + e.getMessage());
            }
            if (digest != null) {
                // we have a winner!
                selectedMda = mda;
                if (trace) log.trace("Selected JCA message digest algorithm '" + jcaName + "', SRP name '" + mda + "'");
                break;
            }
        }
        if (digest == null) {
            selectedMda = "sha-160";
            try {
                digest = MessageDigest.getInstance("SHA-1");
                if (trace) log.trace("Selected default JCA message digest algorithm 'SHA-1' aka SHA-160");
            } catch (NoSuchAlgorithmException e) {
                throw new SaslException("None of the suggested server message digest algorithms are " +
                        "supported, and the default of SHA-160 is also not supported", e);
            }
        }
        clientOptions.getMdaSet().add(selectedMda);

        // Brief intermission to calculate Some Stuff
        final byte[] randomBytes = new byte[64];
        final Random rng = getRandom();
        rng.nextBytes(randomBytes);
        BigInteger a = new BigInteger(1, randomBytes);
        BigInteger A = g.modPow(a, N);
        A_bytes = unsignedByteArrayFor(A);
        final byte[] B_bytes = unsignedByteArrayFor(B);
        final byte[] authenticationIdBytes = bytesOf(authenticationId);
        digest.update(authenticationIdBytes);
        digest.update((byte)':'); // I cheat
        digest.update(bytesOf(password));
        byte[] userPasswordHash = digest.digest();
        digest.update(s_bytes);
        digest.update(userPasswordHash);
        byte[] x_bytes = digest.digest();
        BigInteger x = new BigInteger(1, x_bytes);
        digest.update(A_bytes);
        digest.update(B_bytes);
        byte[] u_bytes = digest.digest();
        BigInteger u = new BigInteger(1, u_bytes);
        BigInteger S_first = B.subtract(THREE.multiply(g.modPow(x, N)).mod(N));
        BigInteger S_second = u.multiply(x).add(a);
        BigInteger S = S_first.modPow(S_second, N);
        K_bytes = digest.digest(unsignedByteArrayFor(S));
        byte[] N_digest = digest.digest(unsignedByteArrayFor(N));
        byte[] g_digest = digest.digest(unsignedByteArrayFor(g));
        byte[] U_digest = digest.digest(authenticationIdBytes);
        I_digest = digest.digest(bytesOf(authorizationId));
        byte[] L_digest = digest.digest(bytesOf(serverOptionString));
        digest.update(bitXor(N_digest, g_digest));
        digest.update(U_digest);
        digest.update(s_bytes);
        digest.update(A_bytes);
        digest.update(B_bytes);
        digest.update(K_bytes);
        digest.update(I_digest);
        digest.update(L_digest);
        M1_bytes = digest.digest();
        cIV_bytes = new byte[64];
        rng.nextBytes(cIV_bytes);

        // Next, negotiate replay detection
        if (requireReplay || serverOptions.isReplayDetectionManditory()) {
            if (! serverOptions.isReplayDetection()) {
                throw new SaslException("Replay detection is required, but the server cannot provide it");
            }
            setReplayEnabled(true);
        }

        // Next, negotiate a message authentication codes (MAC) algorithm (optional)
        if (requireIntegrity || requireReplay || requireConfidentiality || serverOptions.isIntegrityManditory() ||
                serverOptions.isReplayDetectionManditory() || serverOptions.isConfidentialityManditory()) {
            final Set<String> integritySet = serverOptions.getIntegritySet();
            if (! integritySet.contains("hmac-sha-160")) {
                // Server always supports hmac-sha-160
                integritySet.add("hmac-sha-160");
            }
            for (String integrity : integritySet) {
                try {
                    selectIntegrity(integrity, K_bytes);
                    clientOptions.getIntegritySet().add(integrity);
                    break;
                } catch (NoSuchAlgorithmException e) {
                    if (trace) log.trace("Rejected JCA MAC algorithm '" + integrity + "': " + e.getMessage());
                } catch (InvalidKeyException e) {
                    if (trace) log.trace("Rejected key for JCA MAC algorithm '" + integrity + "': " + e.getMessage());
                }
            }
            enableIntegrity();
            if (! isIntegrityEnabled()) {
                if ((serverOptions.isIntegrityManditory() || serverOptions.isReplayDetectionManditory())) {
                    throw new SaslException("Server requires integrity, but none of the suggested server HMAC algorithms are supported");
                }
                if (requireIntegrity) {
                    throw new SaslException("Client requires integrity, but none of the suggested server HMAC algorithms are supported, or there are none");
                }
            }
        }

        if (requireConfidentiality || serverOptions.isConfidentialityManditory()) {
            final Set<String> confidentialitySet = serverOptions.getConfidentialitySet();
            if (! confidentialitySet.contains("aes")) {
                // Server always supports aes
                confidentialitySet.add("aes");
            }
            for (String alg : confidentialitySet) {
                try {
                    selectConfidentiality(alg);
                    clientOptions.getConfidentialitySet().add(alg);
                    log.trace("Selected cipher algorithm '%s'", alg);
                    break;
                } catch (InvalidKeyException e) {
                    log.trace("Rejected JCA Cipher algorithm '%s' (invalid key): %s", alg, e);
                } catch (NoSuchAlgorithmException e) {
                    log.trace("Rejected JCA Cipher algorithm '%s' (no such algorithm): %s", alg, e);
                } catch (NoSuchPaddingException e) {
                    log.trace("Rejected JCA Cipher algorithm '%s' (no such padding): %s", alg, e);
                } catch (InvalidAlgorithmParameterException e) {
                    log.trace("Rejected JCA Cipher algorithm '%s' (invalid algorithm parameter): %s", alg, e);
                }
            }
        }

        buffer = createSrpBuffer();
        writeMpi(buffer, A);
        writeOctetSeq(buffer, M1_bytes);
        clientOptionString = clientOptions.toString();
        writeUtf8(buffer, clientOptionString);
        writeOctetSeq(buffer, cIV_bytes);
        return getSrpBytes(buffer);
    }

    private void complete(final byte[] challenge) throws SaslException {
        // challenge buffer contains useful stuff
        final ByteBuffer challengeBuffer = ByteBuffer.wrap(challenge);
        if (readInteger(challengeBuffer) != challengeBuffer.remaining()) {
            throw new SaslException("Buffer from server has incorrect size");
        }
        final byte[] server_M2_bytes = readOctetSeq(challengeBuffer);
        final byte[] value_sIV_bytes = readOctetSeq(challengeBuffer);
        final byte[] o_digest = digest.digest(bytesOf(clientOptionString));
        String sid = readUtf8(challengeBuffer);
        int ttl = challengeBuffer.getInt();
        digest.update(A_bytes);
        digest.update(M1_bytes);
        digest.update(K_bytes);
        digest.update(I_digest);
        digest.update(o_digest);
        digest.update(bytesOf(sid));
        digest.update((ByteBuffer) ByteBuffer.allocate(4).putInt(ttl).flip());
        final byte[] client_M2_bytes = digest.digest();
        if (! Arrays.equals(server_M2_bytes, client_M2_bytes)) {
            throw new SaslException("Authentication of server failed");
        }
        try {
            enableConfidentiality(K_bytes, value_sIV_bytes, cIV_bytes);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
    }

    public boolean isComplete() {
        return state == State.COMPLETE;
    }

    public void dispose() throws SaslException {
    }

}
