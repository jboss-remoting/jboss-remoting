package org.jboss.cx.remoting.tools;

import java.io.Console;
import java.nio.CharBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.TreeSet;
import org.jboss.cx.remoting.core.security.sasl.SrpVerifier;

/**
 *
 */
public final class PasswordEncoder {
    private static int readInt(Console console, String msg, int defaultVal) {
        final String s = console.readLine("%s [%s]:", msg, Integer.valueOf(defaultVal));
        if (s.trim().isEmpty()) {
            return defaultVal;
        } else {
            return Integer.parseInt(s);
        }
    }

    private static String join(String joiner, Collection<String> c) {
        final Iterator<String> iterator = c.iterator();
        if (! iterator.hasNext()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(iterator.next());
        while (iterator.hasNext()) {
            builder.append(joiner);
            builder.append(iterator.next());
        }
        return builder.toString();
    }

    public static void main(String[] args) {
        final Console console = System.console();
        if (console == null) {
            final RuntimeException ex = new RuntimeException("No console available!");
            ex.setStackTrace(new StackTraceElement[0]);
            throw ex;
        }
        console.printf("JBoss Remoting SRP Password Encoding Tool\n\n");
        final String userName = console.readLine("Enter the new user name: ").trim();
        final char[] passwordOne = console.readPassword("Enter the password: ");
        final char[] passwordTwo = console.readPassword("Re-enter the password: ");
        if (!Arrays.equals(passwordOne, passwordTwo)) {
            console.printf("Error: the passwords do not match!\n");
            return;
        }
        console.printf("\nChoose a message digest algorithm from the following list:\n    %s\n", join(" ", new TreeSet<String>(SrpVerifier.getMessageDigests())));
        final String line = console.readLine(" [sha-256] ");
        final String algorithm = line.trim().isEmpty() ? "sha-256" : line.trim();
        final int primeLength = readInt(console, "Enter preferred prime length in bits", 384);

        final SrpVerifier verifier;
        try {
            verifier = SrpVerifier.generate(passwordOne, primeLength, userName, algorithm);
        } catch (NoSuchAlgorithmException e) {
            final RuntimeException ex = new RuntimeException("No such algorithm! (" + e.getMessage() + ")");
            ex.setStackTrace(new StackTraceElement[0]);
            throw ex;
        }
        CharBuffer buffer = CharBuffer.allocate(1024);
        verifier.writeEncoded(buffer);
        buffer.flip();
        console.printf("\nThe encoded verifier string is:\n\n%s\n", buffer.toString());
        return;
    }
}
