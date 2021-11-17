package com.example.demo;

import java.security.SecureRandom;
import java.util.Random;


public class RandomUuidStringCreator {

    private static final int RANDOM_VERSION = 4;


    public static String getRandomUuid() {
        return getRandomUuid(SecureRandomLazyHolder.SECURE_RANDOM);
    }


    public static String getRandomUuidWithDashes() {
        return format(getRandomUuid());
    }


    public static String getRandomUuid(Random random) {

        long msb = 0;
        long lsb = 0;

        if (random instanceof SecureRandom) {
            final byte[] bytes = new byte[16];
            random.nextBytes(bytes);
            msb = toNumber(bytes, 0, 8);
            lsb = toNumber(bytes, 8, 16);
        } else {
            msb = random.nextLong();
            lsb = random.nextLong();
        }

        msb = (msb & 0xffffffffffff0fffL) | (RANDOM_VERSION & 0x0f) << 12;
        lsb = (lsb & 0x3fffffffffffffffL) | 0x8000000000000000L;

        String msbHex = zerofill(Long.toHexString(msb), 16);
        String lsbHex = zerofill(Long.toHexString(lsb), 16);

        return msbHex + lsbHex;
    }


    public static String getRandomUuidWithDashes(Random random) {
        return format(getRandomUuid(random));
    }

    private static long toNumber(final byte[] bytes, final int start, final int length) {
        long result = 0;
        for (int i = start; i < length; i++) {
            result = (result << 8) | (bytes[i] & 0xff);
        }
        return result;
    }

    private static String zerofill(String string, int length) {
        return new String(lpad(string.toCharArray(), length, '0'));
    }

    private static char[] lpad(char[] chars, int length, char fill) {

        int delta = 0;
        int limit = 0;

        if (length > chars.length) {
            delta = length - chars.length;
            limit = length;
        } else {
            delta = 0;
            limit = chars.length;
        }

        char[] output = new char[chars.length + delta];
        for (int i = 0; i < limit; i++) {
            if (i < delta) {
                output[i] = fill;
            } else {
                output[i] = chars[i - delta];
            }
        }
        return output;
    }

    private static String format(String string) {
        char[] input = string.toCharArray();
        char[] output = new char[36];

        System.arraycopy(input, 0, output, 0, 8);
        System.arraycopy(input, 8, output, 9, 4);
        System.arraycopy(input, 12, output, 14, 4);
        System.arraycopy(input, 16, output, 19, 4);
        System.arraycopy(input, 20, output, 24, 12);

        output[8] = '-';
        output[13] = '-';
        output[18] = '-';
        output[23] = '-';

        return new String(output);
    }

    private static class SecureRandomLazyHolder {
        static final Random SECURE_RANDOM = new SecureRandom();
    }


    public static void main(String[] args) {

        for (int i = 0; i < 1; i++) {
            System.out.println(RandomUuidStringCreator.getRandomUuid());
        }

    }
}