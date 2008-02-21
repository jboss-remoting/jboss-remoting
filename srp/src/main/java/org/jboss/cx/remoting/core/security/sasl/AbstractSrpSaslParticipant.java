package org.jboss.cx.remoting.core.security.sasl;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.jboss.cx.remoting.core.security.sasl.BufferFactory;
import org.jboss.cx.remoting.core.util.IoUtil;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;

/**
 *
 * {@link |http://tools.ietf.org/id/draft-burdis-cat-srp-sasl-08.txt}
 */
public abstract class AbstractSrpSaslParticipant implements NioSaslEndpoint {

    protected static final BigInteger THREE = new BigInteger("3");
    protected final CharsetEncoder utf8Encoder;
    protected final CharsetDecoder utf8Decoder;
    private final BufferFactory bufferFactory;
    protected final CallbackHandler callbackHandler;

    // Map the SRP names for things over to the JCA equivalent
    protected static final Map<String, String> SRP_TO_JCA_MD;
    protected static final Map<String, String> SRP_TO_JCA_HMAC;
    protected static final Map<String, String> SRP_TO_JCA_SBC;
    protected static final Map<String, int[]> SRP_TO_JCA_KEY_SIZES;
    protected static final String namePrompt = "SRP authentication ID: ";
    protected static final String passwordPrompt = "SRP password: ";
    protected static final String mechanismName = "SRP";

    // configuration of active session
    private boolean replayEnabled;
    private boolean integrityEnabled;
    private boolean confidentialityEnabled;
    private Cipher encryptCipher;
    private Cipher decryptCipher;
    private int inboundSequence;
    private int outboundSequence;
    // separate inbound & outbound for concurrency
    private Mac outboundMac;
    private Mac inboundMac;
    private byte[] sessionKey;
    private String jcaEncryptionAlgName;
    private int[] cipherKeySizes;

    static {
        final Map<String, String> hmacMap = new HashMap<String, String>();
        hmacMap.put("hmac-md5", "HmacMD5");
        hmacMap.put("hmac-sha-1", "HmacSHA1");
        hmacMap.put("hmac-sha-160", "HmacSHA1");
        hmacMap.put("hmac-sha-256", "HmacSHA256");
        hmacMap.put("hmac-sha-384", "HmacSHA384");
        hmacMap.put("hmac-sha-512", "HmacSHA512");
        SRP_TO_JCA_HMAC = Collections.unmodifiableMap(hmacMap);
        final Map<String, String> mdMap = new HashMap<String, String>();
        mdMap.put("md2", "MD2");
        mdMap.put("md5", "MD5");
        mdMap.put("sha-1", "SHA-1");
        mdMap.put("sha-160", "SHA-1");
        mdMap.put("sha-256", "SHA-256");
        mdMap.put("sha-384", "SHA-384");
        mdMap.put("sha-512", "SHA-512");
        SRP_TO_JCA_MD = Collections.unmodifiableMap(mdMap);
        final Map<String, String> sbcMap = new HashMap<String, String>();
        sbcMap.put("aes", "AES/CBC/PKCS5Padding");
        sbcMap.put("blowfish", "Blowfish/CBC/PKCS5Padding");
        SRP_TO_JCA_SBC = Collections.unmodifiableMap(sbcMap);
        // This whole thing is lame - there's no way to query valid key sizes - JCA sucks
        final Map<String, int[]> keySizeMap = new HashMap<String, int[]>();
        keySizeMap.put("aes", new int[] { 16, 24, 32 });
        keySizeMap.put("blowfish", new int[] {
                4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22,
                23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39,
                40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56
        });
        SRP_TO_JCA_KEY_SIZES = Collections.unmodifiableMap(keySizeMap);
    }

    protected AbstractSrpSaslParticipant(final CallbackHandler callbackHandler) {
        final Charset charset = Charset.forName("UTF-8");
        utf8Decoder = charset.newDecoder();
        utf8Encoder = charset.newEncoder();
        // TODO - use SASL info for size
        bufferFactory = BufferFactory.create(1024, false);
        this.callbackHandler = callbackHandler;
    }

    protected byte[] bytesOf(final String sequence) throws SaslException {
        try {
            return sequence.getBytes("utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new SaslException("Unable to get bytes for string", e);
        }
    }

    protected ByteBuffer bytesOf(final char[] characters) throws SaslException {
        ByteBuffer buffer = bufferFactory.create();
        saslSrpEncode(CharBuffer.wrap(characters), buffer);
        return (ByteBuffer) buffer.flip();
    }

    protected byte[] bitXor(byte[] target, final byte[] src) {
        final int length = target.length;
        if (length != src.length) {
            throw new IllegalArgumentException("Array length mismatch");
        }
        for (int i = 0; i < length; i ++) {
            target[i] ^= src[i];
        }
        return target;
    }

    protected int readInteger(final ByteBuffer buffer) throws SaslException {
        try {
            return buffer.getInt();
        } catch (BufferUnderflowException ex) {
            throw new SaslException("Garbled message: buffer underflow", ex);
        }
    }

    protected String readUtf8(final ByteBuffer buffer) throws SaslException {
        try {
            final int len = buffer.getShort() & 0xffff;
            final ByteBuffer view = buffer.duplicate();
            final int start = buffer.position();
            view.limit(start + len);
            CharBuffer charBuffer = CharBuffer.allocate(len);
            final CoderResult result = utf8Decoder.decode(view, charBuffer, true);
            if (result.isError()) {
                throw new SaslException("Error while decoding UTF-8 string for SASL SRP exchange");
            } else if (result.isOverflow()) {
                throw new SaslException("Overflow while decoding UTF-8 string for SASL SRP exchange");
            }
            buffer.position(start + len);
            charBuffer.flip();
            return charBuffer.toString();
        } catch (BufferUnderflowException ex) {
            throw new SaslException("Garbled message: buffer underflow", ex);
        }
    }

    protected BigInteger readMpi(final ByteBuffer buffer) throws SaslException {
        try {
            final int len = buffer.getShort() & 0xffff;
            final byte[] data = new byte[len];
            buffer.get(data);
            BigInteger value = new BigInteger(1, data);
            return value;
        } catch (BufferUnderflowException ex) {
            throw new SaslException("Garbled message: buffer underflow", ex);
        }
    }

    protected byte[] readOctetSeq(final ByteBuffer buffer) throws SaslException {
        try {
            final int len = buffer.get() & 0xff;
            final byte[] data = new byte[len];
            buffer.get(data);
            return data;
        } catch (BufferUnderflowException ex) {
            throw new SaslException("Garbled message: buffer underflow", ex);
        }
    }

    protected byte[] getSrpBytes(final ByteBuffer buffer) {
        buffer.flip();
        buffer.putInt(0, buffer.limit() - 4);
        final byte[] dst = new byte[buffer.remaining()];
        buffer.get(dst);
        return dst;
    }

    protected ByteBuffer createSrpBuffer() {
        return bufferFactory.create().putInt(0);
    }

    protected void writeMpi(final ByteBuffer buffer, final BigInteger mpiValue) throws SaslException {
        final byte[] bytes = unsignedByteArrayFor(mpiValue);
        if (bytes.length > 65535) {
            throw new SaslException("Attempted to overflow an SRP multi-precision integer");
        }
        buffer.putShort((short)bytes.length);
        buffer.put(bytes);
    }

    protected void writeOctetSeq(final ByteBuffer buffer, final byte[] octets) throws SaslException {
        final int length = octets.length;
        if (length > 255) {
            throw new SaslException("Attempted to overflow an SRP octet-stream");
        }
        buffer.put((byte) length);
        buffer.put(octets);
    }

    protected void writeUtf8(final ByteBuffer buffer, final CharSequence data) throws SaslException {
        final int pos = buffer.position();
        buffer.putShort((short) 0); // put the string length here...
        final int start = buffer.position();
        final CharBuffer charBuffer = CharBuffer.wrap(data);
        saslSrpEncode(charBuffer, buffer);
        final int end = buffer.position();
        final int length = end - start;
        if (length > 65535) {
            throw new SaslException("String length exceeds maximum allowed by SRP exchange");
        }
        buffer.putShort(pos, (short)length);
    }

    private CoderResult saslSrpEncode(final CharBuffer charBuffer, final ByteBuffer buffer) throws SaslException {
        final CoderResult result = utf8Encoder.encode(charBuffer, buffer, true);
        if (result.isError()) {
            throw new SaslException("Error while encoding UTF-8 string for SASL SRP exchange");
        } else if (result.isOverflow()) {
            throw new SaslException("Overflow while encoding UTF-8 string for SASL SRP exchange");
        }
        return result;
    }

    public abstract boolean isComplete();

    /**
     * Specified by both SaslClient and SaslServer.
     * @param incoming
     * @param offset
     * @param len
     * @return
     * @throws SaslException
     */
    public byte[] unwrap(byte[] incoming, int offset, int len) throws SaslException {
        ByteBuffer incomingBuffer = ByteBuffer.wrap(incoming, offset, len);
        ByteBuffer unwrappedBuffer = createSrpBuffer();
        unwrap(incomingBuffer, unwrappedBuffer);
        // and this is why the default SASL wrap/unwrap sucks
        unwrappedBuffer.flip();
        byte[] output = new byte[unwrappedBuffer.remaining()];
        unwrappedBuffer.get(output);
        return output;
    }

    public byte[] wrap(byte[] outgoing, int offset, int len) throws SaslException {
        ByteBuffer outgoingBuffer = ByteBuffer.wrap(outgoing, offset, len);
        ByteBuffer wrappedBuffer = createSrpBuffer();
        wrap(outgoingBuffer, wrappedBuffer);
        // and this is why the default SASL wrap/unwrap sucks
        wrappedBuffer.flip();
        byte[] output = new byte[wrappedBuffer.remaining()];
        wrappedBuffer.get(output);
        return output;
    }

    public final String getMechanismName() {
        return mechanismName;
    }

    protected byte[] unsignedByteArrayFor(BigInteger unsignedInt) {
        byte[] bytes = unsignedInt.toByteArray();
        // toByteArray may insert a zero byte at the head (to indicate sign), which we drop because we know the value is positive
        // toByteArray will otherwise use the minimum bytes necessary
        if (bytes[0] == 0) {
            // unfortunately this means a copy
            byte[] newBytes = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, newBytes, 0, newBytes.length);
            return newBytes;
        } else {
            return bytes;
        }
    }

    protected boolean isReplayEnabled() {
        return replayEnabled;
    }

    protected void setReplayEnabled(final boolean replayEnabled) {
        this.replayEnabled = replayEnabled;
    }

    protected boolean isIntegrityEnabled() {
        return integrityEnabled;
    }

    protected void selectIntegrity(String algorithmName, byte[] key) throws SaslException, NoSuchAlgorithmException, InvalidKeyException {
        Mac outboundMac, inboundMac;
        outboundMac = Mac.getInstance(algorithmName);
        outboundMac.init(new SecretKeySpec(key, algorithmName));
        inboundMac = Mac.getInstance(algorithmName);
        inboundMac.init(new SecretKeySpec(key, algorithmName));
        this.outboundMac = outboundMac;
        this.inboundMac = inboundMac;
        integrityEnabled = true;
    }

    protected void enableIntegrity() {
        if (outboundMac != null && inboundMac != null) {
            integrityEnabled = true;
        }
    }

    protected boolean isConfidentialityEnabled() {
        return confidentialityEnabled;
    }

    protected void selectConfidentiality(String algorithmName) throws SaslException, NoSuchAlgorithmException, InvalidKeyException, InvalidAlgorithmParameterException, NoSuchPaddingException {
        final Cipher encryptCipher, decryptCipher;
        if (SRP_TO_JCA_SBC.containsKey(algorithmName)) {
            jcaEncryptionAlgName = SRP_TO_JCA_SBC.get(algorithmName);
        } else {
            // Alogorithm not supported.  To add support, update the SRP_TO_JCA_SBC and SRP_TO_JCA_KEY_SIZE maps above
            throw new NoSuchAlgorithmException("This SRP implementation does not support the algorithm \"" + algorithmName + "\"");
        }
        cipherKeySizes = SRP_TO_JCA_KEY_SIZES.get(algorithmName);
        if (cipherKeySizes == null) {
            throw new NoSuchAlgorithmException("Algorithm \"" + algorithmName + "\" does not have any valid key sizes specified");
        }
        encryptCipher = Cipher.getInstance(jcaEncryptionAlgName);
        decryptCipher = Cipher.getInstance(jcaEncryptionAlgName);
        this.encryptCipher = encryptCipher;
        this.decryptCipher = decryptCipher;
    }

    protected void enableConfidentiality(byte[] key, byte[] encryptIV, byte[] decryptIV) throws InvalidKeyException, InvalidAlgorithmParameterException {
        if (encryptCipher != null && decryptCipher != null && cipherKeySizes != null) {
            final int idx = Arrays.binarySearch(cipherKeySizes, key.length);
            if (idx < 0) {
                // key size isn't exact, let's pick the next smaller key size
                final int newIdx = -idx - 2;
                if (newIdx == -1) {
                    throw new InvalidKeyException("Negotiated key is too short to use with this algorithm");
                }
                final int size = cipherKeySizes[newIdx];
                byte[] actualKey = new byte[size];
                System.arraycopy(key, 0, actualKey, 0, size);
                key = actualKey;
            }
            encryptCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, jcaEncryptionAlgName), new IvParameterSpec(encryptIV));
            decryptCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, jcaEncryptionAlgName), new IvParameterSpec(decryptIV));
            confidentialityEnabled = true;
            sessionKey = key;
        }
    }

    public void wrap(ByteBuffer src, ByteBuffer target) throws SaslException {
        if (! isComplete()) {
            throw new SaslException("Attempted to call wrap() with incomplete negotiation");
        }
        // apply encryption, if enabled
        if (confidentialityEnabled) {
            try {
                encryptCipher.doFinal(src, target);
            } catch (ShortBufferException e) {
                throw new SaslException("Encryption failed due to short buffer", e);
            } catch (IllegalBlockSizeException e) {
                throw new SaslException("Encryption failed due to illegal block size", e);
            } catch (BadPaddingException e) {
                throw new SaslException("Encryption failed due to bad padding", e);
            }
        } else {
            target.put(src);
        }
        // apply integrity detection, if enabled, possibly with replay detection
        if (integrityEnabled) {
            try {
                outboundMac.reset();
                if (target.hasArray()) {
                    // it's an array, which ironically means we can do less copying
                    final byte[] bytes = target.array();
                    final int pos = target.position();
                    final int arrayOffset = target.arrayOffset();
                    final int bufferEnd = arrayOffset + pos;
                    outboundMac.update(bytes, arrayOffset, pos);
                    if (replayEnabled) {
                        outboundMac.update((ByteBuffer) ByteBuffer.allocate(4).putInt(outboundSequence++).flip());
                    }
                    outboundMac.doFinal(bytes, bufferEnd);
                    target.position(pos + outboundMac.getMacLength());
                } else {
                    outboundMac.update((ByteBuffer) target.duplicate().flip());
                    byte[] hmacData = outboundMac.doFinal();
                    target.put(hmacData);
                }
            } catch (ShortBufferException e) {
                throw new SaslException("Short buffer failure while wrapping message");
            }
        }
    }

    public void unwrap(ByteBuffer src, ByteBuffer target) throws SaslException {
        if (! isComplete()) {
            throw new SaslException("Attempted to call unwrap() with incomplete negotiation");
        }
        final int macLength = inboundMac.getMacLength();

        final ByteBuffer homeSlice;
        final ByteBuffer macSlice;
        if (integrityEnabled) {
            homeSlice = IoUtil.getSlice(src, -macLength);
            macSlice = IoUtil.getSlice(src, macLength);
            inboundMac.reset();
            inboundMac.update((ByteBuffer) homeSlice.mark());
            if (replayEnabled) {
                inboundMac.update((ByteBuffer) ByteBuffer.allocate(4).putInt(inboundSequence++).flip());
            }
            if (! macSlice.equals(ByteBuffer.wrap(inboundMac.doFinal()))) {
                throw new SaslException("Integrity violation on inbound data");
            }
            homeSlice.reset();
        } else {
            homeSlice = src;
            macSlice = null;
        }
        if (confidentialityEnabled) {
            try {
                decryptCipher.doFinal(homeSlice, target);
            } catch (ShortBufferException e) {
                throw new SaslException("Decryption failed due to short buffer", e);
            } catch (IllegalBlockSizeException e) {
                throw new SaslException("Decryption failed due to illegal block size", e);
            } catch (BadPaddingException e) {
                throw new SaslException("Decryption failed due to bad padding", e);
            }
        } else {
            target.put(homeSlice);
        }
        return;
    }

    public boolean isWrappable() {
        return confidentialityEnabled || integrityEnabled;
    }

    public Object getNegotiatedProperty(String propName) {
        if (! isComplete()) {
            throw new IllegalStateException("SASL authentication not completed");
        }
        if (Sasl.QOP.equals(propName)) {
            if (replayEnabled) {
                if (confidentialityEnabled) {
                    return "auth-conf";
                } else if (integrityEnabled) {
                    return "auth-int";
                }
            } else {
                if (confidentialityEnabled) {
                    return "auth-conf-noreplay";
                } else if (integrityEnabled) {
                    return "auth-int-noreplay";
                }
            }
            return "auth";
        } else if (Sasl.STRENGTH.equals(propName)) {
            if (sessionKey.length > 32) {
                // more than 256 bits
                return "high";
            } else if (sessionKey.length >= 20) {
                // 160-256 bits
                return "medium";
            } else {
                // less than 160
                return "low";
            }
        } else if (Srp.SESSION_KEY.equals(propName)) {
            final SecurityManager securityManager = System.getSecurityManager();
            if (securityManager != null) {
                securityManager.checkPermission(Srp.SESSION_KEY_PERMISSION);
            }
            return sessionKey;
        }
        return null;
    }

    // RNG stuff

    private static final Random seeder;

    static {
        try {
            seeder = SecureRandom.getInstance("SHA1PRNG");
            seeder.nextBytes(new byte[1]);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Cannot initialize RNG", e);
        }
    }

    private static final ThreadLocal<Random> localRandom = new ThreadLocal<Random>();

    protected static final Random getRandom() {
        Random random = localRandom.get();
        if (random == null) {
            try {
                random = SecureRandom.getInstance("SHA1PRNG");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Cannot initialize RNG", e);
            }
            synchronized(seeder) {
                random.setSeed(seeder.nextLong());
            }
            localRandom.set(random);
        }
        return random;
    }
}
