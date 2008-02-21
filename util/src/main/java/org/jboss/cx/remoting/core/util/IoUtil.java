package org.jboss.cx.remoting.core.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

/**
 *
 */
public final class IoUtil {
    public static ByteBuffer getSlice(ByteBuffer source, int length) {
        ByteBuffer slice = source.duplicate();
        final int newLimit;
        if (length < 0) {
            // calculate from end
            newLimit = source.limit() - length;
        } else {
            // calculate from start
            newLimit = source.position() + length;
        }
        slice.limit(newLimit);
        source.position(newLimit);
        return slice;
    }

    public static void base64Decode(CharBuffer source, ByteBuffer target) throws Base64DecodingException {
        int triad;
        while (source.hasRemaining()) {
            triad = 0;
            char ch = source.get();
            if (ch >= 'A' && ch <= 'Z') {
                triad |= (ch - 'A') << 18;
            } else if (ch >= 'a' && ch <= 'z') {
                triad |= (ch - 'a' + 26) << 18;
            } else if (ch >= '0' && ch <= '9') {
                triad |= (ch - '0' + 52) << 18;
            } else if (ch == '+' || ch == '-') {
                triad |= 62 << 18;
            } else if (ch == '/' || ch == '_') {
                triad |= 63 << 18;
            } else if (ch == '=') {
                throw new Base64DecodingException("Unexpected padding encountered");
            }
            if (! source.hasRemaining()) {
                throw new Base64DecodingException("Unexpected end of source data");
            }

            ch = source.get();
            if (ch >= 'A' && ch <= 'Z') {
                triad |= (ch - 'A') << 12;
            } else if (ch >= 'a' && ch <= 'z') {
                triad |= (ch - 'a' + 26) << 12;
            } else if (ch >= '0' && ch <= '9') {
                triad |= (ch - '0' + 52) << 12;
            } else if (ch == '+' || ch == '-') {
                triad |= 62 << 12;
            } else if (ch == '/' || ch == '_') {
                triad |= 63 << 12;
            } else if (ch == '=') {
                throw new Base64DecodingException("Unexpected padding encountered");
            }
            if (! source.hasRemaining()) {
                throw new Base64DecodingException("Unexpected end of source data");
            }

            ch = source.get();
            if (ch >= 'A' && ch <= 'Z') {
                triad |= (ch - 'A') << 6;
            } else if (ch >= 'a' && ch <= 'z') {
                triad |= (ch - 'a' + 26) << 6;
            } else if (ch >= '0' && ch <= '9') {
                triad |= (ch - '0' + 52) << 6;
            } else if (ch == '+' || ch == '-') {
                triad |= 62 << 6;
            } else if (ch == '/' || ch == '_') {
                triad |= 63 << 6;
            } else if (ch == '=') {
                if (source.hasRemaining() && source.get() == '=') {
                    if (! source.hasRemaining()) {
                        target.put((byte) (triad >> 16));
                        return;
                    } else {
                        throw new Base64DecodingException("Extra data after padding");
                    }
                } else {
                    throw new Base64DecodingException("Unexpected end of source data");
                }
            }

            ch = source.get();
            if (ch >= 'A' && ch <= 'Z') {
                triad |= ch - 'A';
            } else if (ch >= 'a' && ch <= 'z') {
                triad |= ch - 'a' + 26;
            } else if (ch >= '0' && ch <= '9') {
                triad |= ch - '0' + 52;
            } else if (ch == '+' || ch == '-') {
                triad |= 62;
            } else if (ch == '/' || ch == '_') {
                triad |= 63;
            } else if (ch == '=') {
                if (! source.hasRemaining()) {
                    target.putShort((short) (triad >> 8));
                    return;
                } else {
                    throw new Base64DecodingException("Extra data after padding");
                }
            }

            target.put((byte) (triad >> 16));
            target.putShort((short) triad);
        }
    }

    private static final char[] base64table = new char[] {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
            'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
            'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X',
            'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
            'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
            'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
            'w', 'x', 'y', 'z', '0', '1', '2', '3',
            '4', '5', '6', '7', '8', '9', '+', '/',
    };

    public static void base64Encode(ByteBuffer source, CharBuffer target) {
        int idx = 0;
        while (source.hasRemaining()) {
            int b = source.get() & 0xff;
            target.put(base64table[b >>> 2]);
            idx = b << 4 & 0x3f;
            if (! source.hasRemaining()) {
                target.put(base64table[idx]);
                target.put("==");
                return;
            }
            b = source.get() & 0xff;
            target.put(base64table[idx | (b >>> 4)]);
            idx = b << 2 & 0x3f;
            if (! source.hasRemaining()) {
                target.put(base64table[idx]);
                target.put('=');
                return;
            }
            b = source.get() & 0xff;
            target.put(base64table[idx | (b >>> 6)]);
            target.put(base64table[b & 0x3f]);
        }
    }
}
