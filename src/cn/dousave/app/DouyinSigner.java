package cn.dousave.app;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/** Java adaptation of Johnserf-Seed/f2 f2/utils/abogus.py, Apache-2.0. */
public final class DouyinSigner {
    private static final String ALPHABET = "Dkdpgh2ZmsQB80/MfvV36XI1R45-WUAlEixNLwoqYTOPuzKFjJnry79HbGcaStCe";
    private static final String UA_ALPHABET = "ckdp1h4ZKsUB80/Mfvw36XIgR25+WQAlEi7NLboqYTOPuzmFjJnryx9HVGDaStCe";
    private static final String FINGERPRINT = "1536|742|1536|864|0|0|0|0|1536|864|1536|864|1536|742|24|24|Win32";
    private static final int[] ORDER = {18,20,52,26,30,34,58,38,40,53,42,21,27,54,55,31,35,57,39,41,43,22,28,32,60,36,23,29,33,37,44,45,59,46,47,48,49,50,24,25,65,66,70,71};
    private static final int[] TABLE = {
        121,243,55,234,103,36,47,228,30,231,106,6,115,95,78,101,250,207,198,50,
        139,227,220,105,97,143,34,28,194,215,18,100,159,160,43,8,169,217,180,120,
        247,45,90,11,27,197,46,3,84,72,5,68,62,56,221,75,144,79,73,161,
        178,81,64,187,134,117,186,118,16,241,130,71,89,147,122,129,65,40,88,150,
        110,219,199,255,181,254,48,4,195,248,208,32,116,167,69,201,17,124,125,104,
        96,83,80,127,236,108,154,126,204,15,20,135,112,158,13,1,188,164,210,237,
        222,98,212,77,253,42,170,202,26,22,29,182,251,10,173,152,58,138,54,141,
        185,33,157,31,252,132,233,235,102,196,191,223,240,148,39,123,92,82,128,109,
        57,24,38,113,209,245,2,119,153,229,189,214,230,174,232,63,52,205,86,140,
        66,175,111,171,246,133,238,193,99,60,74,91,225,51,76,37,145,211,166,151,
        213,206,0,200,244,176,218,44,184,172,49,216,93,168,53,21,183,41,67,85,
        224,155,226,242,87,177,146,70,190,12,162,19,137,114,25,165,163,192,23,59,
        9,94,179,107,35,7,142,131,239,203,149,136,61,249,14,156
    };

    private DouyinSigner() {}

    public static String sign(String query, String userAgent) {
        long started = System.currentTimeMillis();
        byte[] parameters = sm3(sm3((query + "cus").getBytes(StandardCharsets.UTF_8)));
        byte[] body = sm3(sm3("cus".getBytes(StandardCharsets.UTF_8)));
        String encodedUa = encode(rc4(new byte[] {0, 1, 14}, userAgent.getBytes(StandardCharsets.UTF_8)), UA_ALPHABET);
        byte[] ua = sm3(encodedUa.getBytes(StandardCharsets.UTF_8));
        long ended = System.currentTimeMillis();
        int[] values = new int[72];
        values[18] = 44;
        putTime(values, 20, started);
        values[31] = 1;
        values[37] = 14;
        values[38] = parameters[21] & 255;
        values[39] = parameters[22] & 255;
        values[40] = body[21] & 255;
        values[41] = body[22] & 255;
        values[42] = ua[23] & 255;
        values[43] = ua[24] & 255;
        values[44] = (int) (ended >> 24) & 255;
        values[45] = (int) (ended >> 16) & 255;
        values[46] = (int) (ended >> 8) & 255;
        values[47] = (int) ended & 255;
        values[48] = 3;
        values[49] = (int) (ended >> 32) & 255;
        values[50] = (int) (ended >> 40) & 255;
        values[57] = 6383 & 255;
        values[58] = (6383 >> 8) & 255;
        values[65] = FINGERPRINT.length();
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        int checksum = 0;
        for (int index : ORDER) {
            plain.write(values[index]);
            checksum ^= values[index];
        }
        byte[] fingerprint = FINGERPRINT.getBytes(StandardCharsets.US_ASCII);
        plain.write(fingerprint, 0, fingerprint.length);
        plain.write(checksum);
        byte[] payload = transform(plain.toByteArray());
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        SecureRandom random = new SecureRandom();
        for (int index = 0; index < 3; index++) {
            int number = random.nextInt(10000);
            result.write((number & 170) | 1);
            result.write((number & 85) | 2);
            result.write(((number >> 8) & 170) | 5);
            result.write(((number >> 8) & 85) | 40);
        }
        result.write(payload, 0, payload.length);
        return encode(result.toByteArray(), ALPHABET);
    }

    private static void putTime(int[] values, int offset, long time) {
        values[offset] = (int) (time >> 24) & 255;
        values[offset + 1] = (int) (time >> 16) & 255;
        values[offset + 2] = (int) (time >> 8) & 255;
        values[offset + 3] = (int) time & 255;
        values[offset + 4] = (int) (time >> 32) & 255;
        values[offset + 5] = (int) (time >> 40) & 255;
    }

    private static byte[] transform(byte[] input) {
        int[] table = TABLE.clone();
        int indexB = table[1];
        int initial = table[indexB];
        int valueE = 0;
        byte[] output = new byte[input.length];
        for (int index = 0; index < input.length; index++) {
            int sum;
            if (index == 0) {
                sum = indexB + initial;
                table[1] = initial;
                table[indexB] = indexB;
            } else {
                sum = initial + valueE;
            }
            output[index] = (byte) ((input[index] & 255) ^ table[sum % 256]);
            valueE = table[(index + 2) % 256];
            sum = (indexB + valueE) % 256;
            initial = table[sum];
            table[sum] = table[(index + 2) % 256];
            table[(index + 2) % 256] = initial;
            indexB = sum;
        }
        return output;
    }

    private static byte[] rc4(byte[] key, byte[] input) {
        int[] state = new int[256];
        for (int index = 0; index < 256; index++) {
            state[index] = index;
        }
        int j = 0;
        for (int index = 0; index < 256; index++) {
            j = (j + state[index] + (key[index % key.length] & 255)) & 255;
            int old = state[index];
            state[index] = state[j];
            state[j] = old;
        }
        int i = 0;
        j = 0;
        byte[] output = new byte[input.length];
        for (int index = 0; index < input.length; index++) {
            i = (i + 1) & 255;
            j = (j + state[i]) & 255;
            int old = state[i];
            state[i] = state[j];
            state[j] = old;
            output[index] = (byte) ((input[index] & 255) ^ state[(state[i] + state[j]) & 255]);
        }
        return output;
    }

    private static String encode(byte[] input, String alphabet) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < input.length; index += 3) {
            int value = (input[index] & 255) << 16;
            if (index + 1 < input.length) {
                value |= (input[index + 1] & 255) << 8;
            }
            if (index + 2 < input.length) {
                value |= input[index + 2] & 255;
            }
            result.append(alphabet.charAt((value >>> 18) & 63));
            result.append(alphabet.charAt((value >>> 12) & 63));
            if (index + 1 < input.length) {
                result.append(alphabet.charAt((value >>> 6) & 63));
            }
            if (index + 2 < input.length) {
                result.append(alphabet.charAt(value & 63));
            }
        }
        while (result.length() % 4 != 0) {
            result.append('=');
        }
        return result.toString();
    }

    public static byte[] sm3(byte[] input) {
        int length = ((input.length + 9 + 63) / 64) * 64;
        byte[] padded = new byte[length];
        System.arraycopy(input, 0, padded, 0, input.length);
        padded[input.length] = (byte) 128;
        long bits = (long) input.length * 8;
        for (int index = 0; index < 8; index++) {
            padded[length - 1 - index] = (byte) (bits >>> (index * 8));
        }
        int[] state = {0x7380166f,0x4914b2b9,0x172442d7,0xda8a0600,0xa96f30bc,0x163138aa,0xe38dee4d,0xb0fb0e4e};
        for (int offset = 0; offset < length; offset += 64) {
            int[] words = new int[68];
            for (int index = 0; index < 16; index++) {
                int position = offset + index * 4;
                words[index] = ((padded[position] & 255) << 24) | ((padded[position + 1] & 255) << 16)
                    | ((padded[position + 2] & 255) << 8) | (padded[position + 3] & 255);
            }
            for (int index = 16; index < 68; index++) {
                int value = words[index - 16] ^ words[index - 9] ^ Integer.rotateLeft(words[index - 3], 15);
                words[index] = value ^ Integer.rotateLeft(value, 15) ^ Integer.rotateLeft(value, 23)
                    ^ Integer.rotateLeft(words[index - 13], 7) ^ words[index - 6];
            }
            int a = state[0];
            int b = state[1];
            int c = state[2];
            int d = state[3];
            int e = state[4];
            int f = state[5];
            int g = state[6];
            int h = state[7];
            for (int round = 0; round < 64; round++) {
                int constant = 0x7a879d8a;
                int ff = (a & b) | (a & c) | (b & c);
                int gg = (e & f) | (~e & g);
                if (round < 16) {
                    constant = 0x79cc4519;
                    ff = a ^ b ^ c;
                    gg = e ^ f ^ g;
                }
                int rotated = Integer.rotateLeft(a, 12);
                int first = Integer.rotateLeft(rotated + e + Integer.rotateLeft(constant, round), 7);
                int second = first ^ rotated;
                int nextA = ff + d + second + (words[round] ^ words[round + 4]);
                int nextE = gg + h + first + words[round];
                d = c;
                c = Integer.rotateLeft(b, 9);
                b = a;
                a = nextA;
                h = g;
                g = Integer.rotateLeft(f, 19);
                f = e;
                e = nextE ^ Integer.rotateLeft(nextE, 9) ^ Integer.rotateLeft(nextE, 17);
            }
            int[] compressed = {a,b,c,d,e,f,g,h};
            for (int index = 0; index < 8; index++) {
                state[index] ^= compressed[index];
            }
        }
        byte[] output = new byte[32];
        for (int index = 0; index < 8; index++) {
            output[index * 4] = (byte) (state[index] >>> 24);
            output[index * 4 + 1] = (byte) (state[index] >>> 16);
            output[index * 4 + 2] = (byte) (state[index] >>> 8);
            output[index * 4 + 3] = (byte) state[index];
        }
        return output;
    }
}
