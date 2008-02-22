package org.jboss.cx.remoting.core.security.sasl;

import java.io.Serializable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.Set;
import org.jboss.cx.remoting.util.Base64DecodingException;
import org.jboss.cx.remoting.util.IoUtil;

/**
 *
 */
public final class SrpVerifier implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String messageDigestName;
    private final int generator;
    private final BigInteger salt;
    private final BigInteger safePrime;
    private final BigInteger verifier;

    public SrpVerifier(final String messageDigestName, final int generator, final BigInteger salt, final BigInteger safePrime, final BigInteger verifier) {
        if (messageDigestName == null) {
            throw new NullPointerException("Null message digest name");
        }
        if (salt == null) {
            throw new NullPointerException("Null salt value");
        }
        if (safePrime == null) {
            throw new NullPointerException("Null safe prime");
        }
        if (verifier == null) {
            throw new NullPointerException("Null verifier");
        }
        this.messageDigestName = messageDigestName;
        this.generator = generator;
        this.salt = salt;
        this.safePrime = safePrime;
        this.verifier = verifier;
    }

    public static SrpVerifier readEncoded(final CharBuffer source) throws Base64DecodingException {
        StringBuilder digestNameBuilder = new StringBuilder();
        for (; ;) {
            final char c = source.get();
            if (c == ':') {
                break;
            }
            digestNameBuilder.append(c);
        }
        if (!source.hasRemaining()) {
            throw new IllegalArgumentException("Wrong format for SRP verifier");
        }
        String messageDigestName = digestNameBuilder.toString();
        ByteBuffer target = ByteBuffer.allocate(512);
        IoUtil.base64Decode(source, target);
        target.flip();
        int length;
        byte[] byteData;
        length = target.getInt();
        target.get(byteData = new byte[length]);
        int generator = new BigInteger(1, byteData).intValue();
        length = target.getInt();
        target.get(byteData = new byte[length]);
        BigInteger salt = new BigInteger(1, byteData);
        length = target.getInt();
        target.get(byteData = new byte[length]);
        BigInteger safePrime = new BigInteger(1, byteData);
        length = target.getInt();
        target.get(byteData = new byte[length]);
        BigInteger verifier = new BigInteger(1, byteData);
        if (target.hasRemaining()) {
            throw new IllegalArgumentException("Trailing junk in verifier data");
        }
        return new SrpVerifier(messageDigestName, generator, salt, safePrime, verifier);
    }

    public static Set<String> getMessageDigests() {
        return AbstractSrpSaslParticipant.SRP_TO_JCA_MD.keySet();
    }

    public static SrpVerifier generate(final char[] password, int preferredPrimeLength, String userName, String srpAlgorithmName) throws NoSuchAlgorithmException {
        if (! AbstractSrpSaslParticipant.SRP_TO_JCA_MD.containsKey(srpAlgorithmName)) {
            throw new IllegalArgumentException("Invalid SRP message digest algorithm given");
        }
        final MessageDigest messageDigest = MessageDigest.getInstance(AbstractSrpSaslParticipant.SRP_TO_JCA_MD.get(srpAlgorithmName));
        return generate(password, preferredPrimeLength, userName, srpAlgorithmName, messageDigest, Charset.forName("utf-8").newEncoder(), null);
    }

    public static SrpVerifier generate(final char[] password, int preferredPrimeLength, String userName, String srpAlgorithmName, MessageDigest messageDigest, CharsetEncoder encoder, Random rng) {
        final int length = messageDigest.getDigestLength();
        byte[] saltBytes = new byte[length];
        rng.nextBytes(saltBytes);
        final BigInteger salt = new BigInteger(1, saltBytes);
        ByteBuffer workspace = ByteBuffer.allocate(512);
        encoder.encode(CharBuffer.wrap(userName), workspace, true);
        workspace.put((byte) ':');
        encoder.encode(CharBuffer.wrap(password), workspace, true);
        workspace.flip();
        byte[] bufBytes = workspace.array();
        messageDigest.update(bufBytes, workspace.arrayOffset(), workspace.remaining());
        final byte[] digestOutput = messageDigest.digest();
        messageDigest.update(saltBytes);
        messageDigest.update(digestOutput);
        final byte[] xb = messageDigest.digest();
        final BigInteger x = new BigInteger(1, xb);
        Prime primeChoice = null;
        // Could do a binary search for this, but the list is so short that I can't imagine it would be any cheaper
        for (Prime prime : primes) {
            if (prime.bits > preferredPrimeLength) {
                primeChoice = prime;
                break;
            }
        }
        if (primeChoice == null) {
            primeChoice = primes[primes.length - 1];
        }
        final BigInteger g = primeChoice.generator;
        final BigInteger N = primeChoice.prime;
        final BigInteger v = g.modPow(x, N);
        return new SrpVerifier(srpAlgorithmName, g.intValue(), salt, N, v);
    }

    public static boolean isKnownPrime(BigInteger primeInteger, BigInteger generator) {
        for (Prime prime : primes) {
            if (generator.equals(prime.generator) && primeInteger.equals(prime.prime)) {
                return true;
            }
        }
        return false;
    }

    public String getMessageDigestName() {
        return messageDigestName;
    }

    public int getGenerator() {
        return generator;
    }

    public BigInteger getSalt() {
        return salt;
    }

    public BigInteger getSafePrime() {
        return safePrime;
    }

    public BigInteger getVerifier() {
        return verifier;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder(100);
        builder.append(getClass().getName());
        builder.append("@");
        builder.append(Integer.toHexString(hashCode()));
        builder.append("/");
        builder.append(Integer.toHexString(System.identityHashCode(this)));
        builder.append(": digest=\"");
        builder.append(messageDigestName);
        builder.append("\", generator (g)=");
        builder.append(generator);
        builder.append(", salt (s)=");
        builder.append(salt.toString());
        builder.append(", safe prime (N)=");
        builder.append(safePrime.toString());
        builder.append(", verifier (v)=");
        builder.append(verifier.toString());
        return builder.toString();
    }

    /**
     * Write the encoded string to the target charbuffer.
     *
     * @param target the target buffer for the encoded string
     */
    public void writeEncoded(CharBuffer target) {
        target.put(messageDigestName);
        target.put(':');
        ByteBuffer byteData = ByteBuffer.allocate(800);
        byteData.putInt(4);
        byteData.putInt(generator);
        writeIntegerBytes(salt.toByteArray(), byteData);
        writeIntegerBytes(safePrime.toByteArray(), byteData);
        writeIntegerBytes(verifier.toByteArray(), byteData);
        byteData.flip();
        IoUtil.base64Encode(byteData, target);
    }

    private void writeIntegerBytes(final byte[] integerBytes, final ByteBuffer byteData) {
        if (integerBytes[0] == 0) {
            byteData.putInt(integerBytes.length - 1);
            byteData.put(integerBytes, 1, integerBytes.length - 1);
        } else {
            byteData.putInt(integerBytes.length);
            byteData.put(integerBytes);
        }
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof SrpVerifier)) {
            return false;
        }
        SrpVerifier other = (SrpVerifier) obj;
        return other.messageDigestName.equals(messageDigestName) &&
                other.generator == generator &&
                other.salt.equals(salt) &&
                other.safePrime.equals(safePrime) &&
                other.verifier.equals(verifier);
    }

    private int hashCode = 0;

    private boolean calculated = false;

    public int hashCode() {
        if (calculated) {
            return hashCode;
        }
        int result;
        result = messageDigestName.hashCode();
        result = 31 * result + generator;
        result = 31 * result + salt.hashCode();
        result = 31 * result + safePrime.hashCode();
        result = 31 * result + verifier.hashCode();
        hashCode = result;
        calculated = true;
        return result;
    }

    private static final class Prime {
        private final int bits;
        private final BigInteger prime;
        private final BigInteger generator;

        private Prime(final int bits, final BigInteger prime, final BigInteger generator) {
            this.bits = bits;
            this.prime = prime;
            this.generator = generator;
        }
    }

    private static final BigInteger TWO = new BigInteger("2");

    /**
     * These are the safe primes listed in draft-burdis-cat-srp-sasl-08.
     */
    private static final Prime[] primes = {
            // Um, this looks like 260 bits to me
            new Prime(264, new BigInteger("" +
                    "115B8B692E0E045692CF280B436735C7" +
                    "7A5A9E8A9E7ED56C965F87DB5B2A2ECE" +
                    "3", 16), TWO),
            new Prime(384, new BigInteger("" +
                    "8025363296FB943FCE54BE717E0E2958" +
                    "A02A9672EF561953B2BAA3BAACC3ED57" +
                    "54EB764C7AB7184578C57D5949CCB41B", 16), TWO),
            new Prime(512, new BigInteger("" +
                    "D4C7F8A2B32C11B8FBA9581EC4BA4F1B" +
                    "04215642EF7355E37C0FC0443EF756EA" +
                    "2C6B8EEB755A1C723027663CAA265EF7" +
                    "85B8FF6A9B35227A52D86633DBDFCA43", 16), TWO),
            new Prime(640, new BigInteger("" +
                    "C94D67EB5B1A2346E8AB422FC6A0EDAE" +
                    "DA8C7F894C9EEEC42F9ED250FD7F0046" +
                    "E5AF2CF73D6B2FA26BB08033DA4DE322" +
                    "E144E7A8E9B12A0E4637F6371F34A207" +
                    "1C4B3836CBEEAB15034460FAA7ADF483", 16), TWO),
            new Prime(768, new BigInteger("" +
                    "B344C7C4F8C495031BB4E04FF8F84EE9" +
                    "5008163940B9558276744D91F7CC9F40" +
                    "2653BE7147F00F576B93754BCDDF71B6" +
                    "36F2099E6FFF90E79575F3D0DE694AFF" +
                    "737D9BE9713CEF8D837ADA6380B1093E" +
                    "94B6A529A8C6C2BE33E0867C60C3262B", 16), TWO),
            new Prime(1024, new BigInteger("" +
                    "EEAF0AB9ADB38DD69C33F80AFA8FC5E8" +
                    "6072618775FF3C0B9EA2314C9C256576" +
                    "D674DF7496EA81D3383B4813D692C6E0" +
                    "E0D5D8E250B98BE48E495C1D6089DAD1" +
                    "5DC7D7B46154D6B6CE8EF4AD69B15D49" +
                    "82559B297BCF1885C529F566660E57EC" +
                    "68EDBC3C05726CC02FD4CBF4976EAA9A" +
                    "FD5138FE8376435B9FC61D2FC0EB06E3", 16), TWO),
            new Prime(1280, new BigInteger("" +
                    "D77946826E811914B39401D56A0A7843" +
                    "A8E7575D738C672A090AB1187D690DC4" +
                    "3872FC06A7B6A43F3B95BEAEC7DF04B9" +
                    "D242EBDC481111283216CE816E004B78" +
                    "6C5FCE856780D41837D95AD787A50BBE" +
                    "90BD3A9C98AC0F5FC0DE744B1CDE1891" +
                    "690894BC1F65E00DE15B4B2AA6D87100" +
                    "C9ECC2527E45EB849DEB14BB2049B163" +
                    "EA04187FD27C1BD9C7958CD40CE7067A" +
                    "9C024F9B7C5A0B4F5003686161F0605B", 16), TWO),
            new Prime(1536, new BigInteger("" +
                    "9DEF3CAFB939277AB1F12A8617A47BBB" +
                    "DBA51DF499AC4C80BEEEA9614B19CC4D" +
                    "5F4F5F556E27CBDE51C6A94BE4607A29" +
                    "1558903BA0D0F84380B655BB9A22E8DC" +
                    "DF028A7CEC67F0D08134B1C8B9798914" +
                    "9B609E0BE3BAB63D47548381DBC5B1FC" +
                    "764E3F4B53DD9DA1158BFD3E2B9C8CF5" +
                    "6EDF019539349627DB2FD53D24B7C486" +
                    "65772E437D6C7F8CE442734AF7CCB7AE" +
                    "837C264AE3A9BEB87F8A2FE9B8B5292E" +
                    "5A021FFF5E91479E8CE7A28C2442C6F3" +
                    "15180F93499A234DCF76E3FED135F9BB", 16), TWO),
            new Prime(2048, new BigInteger("" +
                    "AC6BDB41324A9A9BF166DE5E1389582F" +
                    "AF72B6651987EE07FC3192943DB56050" +
                    "A37329CBB4A099ED8193E0757767A13D" +
                    "D52312AB4B03310DCD7F48A9DA04FD50" +
                    "E8083969EDB767B0CF6095179A163AB3" +
                    "661A05FBD5FAAAE82918A9962F0B93B8" +
                    "55F97993EC975EEAA80D740ADBF4FF74" +
                    "7359D041D5C33EA71D281E446B14773B" +
                    "CA97B43A23FB801676BD207A436C6481" +
                    "F1D2B9078717461A5B9D32E688F87748" +
                    "544523B524B0D57D5EA77A2775D2ECFA" +
                    "032CFBDBF52FB3786160279004E57AE6" +
                    "AF874E7303CE53299CCC041C7BC308D8" +
                    "2A5698F3A8D0C38271AE35F8E9DBFBB6" +
                    "94B5C803D89F7AE435DE236D525F5475" +
                    "9B65E372FCD68EF20FA7111F9E4AFF73", 16), TWO),
    };
}
