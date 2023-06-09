/*
 * FreeOTP
 *
 * Authors: Nathaniel McCallum <npmccallum@redhat.com>
 *
 * Copyright (C) 2018  Nathaniel McCallum, Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fedorahosted.freeotp;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.codec.binary.Base32;
import org.fedorahosted.freeotp.utils.Time;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class Token {
    public static class UnsafeUriException        extends Exception {}
    public static class UnsafeSecretException     extends UnsafeUriException {}
    public static class UnsafeDigitsException     extends UnsafeUriException {}
    public static class UnsafeAlgorithmException  extends UnsafeUriException {}

    public static class InvalidUriException       extends Exception {}
    public static class InvalidCounterException   extends InvalidUriException {}
    public static class InvalidDigitsException    extends InvalidUriException {}
    public static class InvalidPeriodException    extends InvalidUriException {}
    public static class InvalidSecretException    extends InvalidUriException {}
    public static class InvalidLabelException     extends InvalidUriException {}
    public static class InvalidAlgorithmException extends InvalidUriException {}
    public static class InvalidSchemeException    extends InvalidUriException {}
    public static class InvalidTypeException      extends InvalidUriException {}
    public static class InvalidColorException     extends InvalidUriException {}

    public enum Type { HOTP, TOTP }

    private static final String[] SAFE_ALGOS = { "SHA1", "SHA224", "SHA256", "SHA384", "SHA512" };
    private static final Pattern PATTERN = Pattern.compile("^/(?:([^:]+):)?([^:]+)$");

    @SerializedName("algo")
    private final String mAlgorithm;

    @SerializedName("issuerExt")
    private String mIssuer;

    @SerializedName("issuerInt")
    private final String mIssuerParam;

    @SerializedName("issuerAlt")
    private final String mIssuerAlt;

    @SerializedName("label")
    private String mLabel;

    @SerializedName("labelAlt")
    private String mLabelAlt;

    @SerializedName("image")
    private final String mImage;

    @SerializedName("color")
    private final String mColor;

    @SerializedName("lock")
    private final Boolean mLock;

    @SerializedName("period")
    private final Integer mPeriod;

    @SerializedName("digits")
    private final Integer mDigits;

    @SerializedName("type")
    private final Type mType;

    @SerializedName("counter")
    private Long mCounter;

    private class Secret {
        private byte[] secret;
    }

    public static Token deserialize(String json) {
        return new Gson().fromJson(json, Token.class);
    }

    public static Pair<SecretKey, Token> random() {
        Random r = ThreadLocalRandom.current();
        Token t = new Token(r);

        byte[] bytes = new byte[16 + r.nextInt(16)];
        r.nextBytes(bytes);
        return new Pair<SecretKey, Token>(new SecretKeySpec(bytes, "Hmac" + t.getAlgorithm()), t);
    }

    private static final String[] ISSUERS = { "Buffer", "Google+", "HootSuite", "Mastodon",
            "Reddit", "Tumbler", "Twitter", "WordPress.com", "FreeIPA", "Facebook", "Steam",
            "Bitbucket", "gitlab.com", "Code Climate", "GitHub", "Launchpad", "Mapbox" };

    private Token(Random r) {
        mIssuer = r.nextInt(5) < 1 ? null : ISSUERS[r.nextInt(ISSUERS.length)];
        mIssuerAlt = mIssuer;
        mIssuerParam = mIssuer;
        mAlgorithm = SAFE_ALGOS[r.nextInt(SAFE_ALGOS.length)];
        mType = r.nextBoolean() ? Type.TOTP : Type.HOTP;
        mLabel = UUID.randomUUID().toString();
        mLabelAlt = mLabel;
        mPeriod = 5 + r.nextInt(55);
        mCounter = (long) r.nextInt(1000);
        mLock = r.nextBoolean();

        Code.Factory f = Code.Factory.fromIssuer(mIssuer);
        mDigits = f.getDigitsMin() + r.nextInt(f.getDigitsMax() - f.getDigitsMin());

        mImage = null;
        mColor = null;
    }

    private String getAlgorithm() {
        return mAlgorithm == null ? "SHA1" : mAlgorithm;
    }

    public String serialize() {
        return new Gson().toJson(this);
    }

    public String getIssuer() {
        return mIssuer == null ? mIssuerParam : mIssuer;
    }

    public String getIssuerAlt() {
        return mIssuerAlt;
    }

    public String getLabel() {
        return mLabel;
    }

    public String getLabelAlt() {
        return mLabelAlt;
    }

    public int getPeriod() {
        return mPeriod == null ? 30 : mPeriod;
    }

    public String getImage() {
        return mImage;
    }

    public String getColor() {
        return mColor;
    }

    public Type getType() {
        return mType;
    }

    public boolean getLock() {
        return mLock == null ? false : mLock;
    }

    public Code getCode(Key key) throws InvalidKeyException {
        Mac mac;

        // Prepare the input.
        System.out.println(String.format("token.GetCode(): prepare input"));
        ByteBuffer bb = ByteBuffer.allocate(8);
        bb.order(ByteOrder.BIG_ENDIAN);
        switch (mType) {
            case HOTP: bb.putLong(mCounter++); break;
            case TOTP: bb.putLong(Time.INSTANCE.current() / 1000 / getPeriod()); break;
        }

        try {
            mac = Mac.getInstance("Hmac" + getAlgorithm());
        } catch (NoSuchAlgorithmException e) {
            mac = null; // This should never happen since we check validity in the constructor.
        }

        // Do the hashing.
        mac.init(key);
        byte[] digest = mac.doFinal(bb.array());

        // Truncate.
        int off = digest[digest.length - 1] & 0xf;
        int code = (digest[off] & 0x7f) << 0x18;
        code |= (digest[off + 1] & 0xff) << 0x10;
        code |= (digest[off + 2] & 0xff) << 0x08;
        code |= (digest[off + 3] & 0xff);

        return Code.Factory.fromIssuer(mIssuer).makeCode(code, mDigits, getPeriod());
    }

    public void setIssuer(String issuer) {
        mIssuer = issuer;
    }

    public void setLabel(String label) {
        mLabel = label;
    }
}
