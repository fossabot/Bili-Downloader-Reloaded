package me.heartalborada.biliDownloader.Bili;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import me.heartalborada.biliDownloader.Bili.beans.countrySMS;
import me.heartalborada.biliDownloader.Bili.beans.geetestVerify;
import me.heartalborada.biliDownloader.Bili.beans.loginData;
import me.heartalborada.biliDownloader.Bili.exceptions.BadRequestDataException;
import me.heartalborada.biliDownloader.Bili.interfaces.Callback;
import me.heartalborada.biliDownloader.utils.okhttp.simpleCookieJar;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

public class biliInstance {
    private final int[] mixinKeyEncTab = new int[]{
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
            33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
            61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
            36, 20, 34, 44, 52
    };
    private final simpleCookieJar simpleCookieJar;
    private final OkHttpClient client;
    private String signature;
    public biliInstance() throws IOException {
        simpleCookieJar = new simpleCookieJar();
        client = new OkHttpClient.Builder()
                .addInterceptor(new headerInterceptor())
                .cookieJar(simpleCookieJar)
                .build();
        signature = upgradeWbiSign();
    }

    public biliInstance(HashMap<String, List<Cookie>> CookieData) throws IOException {
        simpleCookieJar = new simpleCookieJar(CookieData);
        client = new OkHttpClient.Builder()
                .addInterceptor(new headerInterceptor())
                .cookieJar(simpleCookieJar)
                .build();
        signature = upgradeWbiSign();
    }

    private static String generateMD5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    protected TreeMap<String, String> signParameters(Map<String, String> parameters) throws IOException {
        TreeMap<String, String> copy = new TreeMap<>(parameters);
        copy.put("wts", String.valueOf(System.currentTimeMillis() / 1000));
        StringJoiner paramStr = new StringJoiner("&");
        copy.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        entry ->
                                paramStr.add(entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                );
        if (signature == null)
            signature = upgradeWbiSign();
        copy.put("w_rid", generateMD5(paramStr + signature));
        return copy;
    }

    protected String upgradeWbiSign() throws IOException {
        Request req = new Request.Builder().url("https://api.bilibili.com/x/web-interface/nav").build();
        String imgAndSub;
        try (Response resp = client.newCall(req).execute()) {
            if (resp.body() != null) {
                String str = resp.body().string();
                JsonObject element = JsonParser.parseString(str).getAsJsonObject();
                JsonObject wbiData = element.getAsJsonObject().getAsJsonObject("data").getAsJsonObject("wbi_img");
                String img = wbiData.getAsJsonPrimitive("img_url").getAsString();
                img = img.substring(img.lastIndexOf('/') + 1, img.lastIndexOf("."));

                String sub = wbiData.getAsJsonPrimitive("sub_url").getAsString();
                sub = sub.substring(sub.lastIndexOf('/') + 1, sub.lastIndexOf("."));
                imgAndSub = img + sub;
            } else {
                throw new IOException("Empty body");
            }
        }
        StringBuilder signatureTemp = new StringBuilder();
        for (int i : mixinKeyEncTab) {
            signatureTemp.append(imgAndSub.charAt(i));
        }
        return signatureTemp.substring(0, 32);
    }

    public geetestVerify getNewGeetestCaptcha() throws IOException {
        Request req = new Request.Builder().url("https://passport.bilibili.com/x/passport-login/captcha?source=main_web").build();
        try (Response resp = client.newCall(req).execute()) {
            if (resp.body() != null) {
                String str = resp.body().string();
                JsonObject object = JsonParser.parseString(str).getAsJsonObject();
                if (object.getAsJsonPrimitive("code").getAsInt() != 0) {
                    throw new BadRequestDataException(
                            object.getAsJsonPrimitive("code").getAsInt(),
                            object.getAsJsonPrimitive("message").getAsString()
                    );
                }
                object = object.getAsJsonObject("data");
                return new geetestVerify(
                        object.getAsJsonObject("geetest").getAsJsonPrimitive("challenge").getAsString(),
                        object.getAsJsonObject("geetest").getAsJsonPrimitive("gt").getAsString(),
                        object.getAsJsonPrimitive("token").getAsString()
                );
            } else {
                throw new IOException("Empty body");
            }
        }
    }

    private static class headerInterceptor implements Interceptor {

        @NotNull
        @Override
        public Response intercept(@NotNull Chain chain) throws IOException {
            Request.Builder b = chain.request().newBuilder();
            b.addHeader("Origin", "https://www.bilibili.com/");
            b.addHeader("Referer", "https://www.bilibili.com");
            b.addHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Safari/537.36 Edg/111.0.1661.24");
            return chain.proceed(b.build());
        }
    }

    public class Login {
        public class SMS {
            public LinkedList<countrySMS> getCountryList() throws IOException {
                Request req = new Request.Builder().url("https://passport.bilibili.com/web/generic/country/list").build();
                LinkedList<countrySMS> list = new LinkedList<>();
                try (Response resp = client.newCall(req).execute()) {
                    if (resp.body() != null) {
                        String str = resp.body().string();
                        JsonObject object = JsonParser.parseString(str).getAsJsonObject();
                        if (object.getAsJsonPrimitive("code").getAsInt() != 0) {
                            throw new BadRequestDataException(
                                    object.getAsJsonPrimitive("code").getAsInt(),
                                    object.getAsJsonPrimitive("message").getAsString()
                            );
                        }
                        JsonObject data = object.getAsJsonObject("data");
                        for (JsonElement o : data.getAsJsonArray("common")) {
                            JsonObject o1 = o.getAsJsonObject();
                            list.add(new countrySMS(
                                    o1.getAsJsonPrimitive("id").getAsInt(),
                                    o1.getAsJsonPrimitive("cname").getAsString(),
                                    o1.getAsJsonPrimitive("country_id").getAsInt()
                            ));
                        }
                        for (JsonElement o : data.getAsJsonArray("others")) {
                            JsonObject o1 = o.getAsJsonObject();
                            list.add(new countrySMS(
                                    o1.getAsJsonPrimitive("id").getAsInt(),
                                    o1.getAsJsonPrimitive("cname").getAsString(),
                                    o1.getAsJsonPrimitive("country_id").getAsInt()
                            ));
                        }
                    } else {
                        throw new IOException("Empty body");
                    }
                }
                return list;
            }

            public String sendSMSCode(long tel, int countryID, geetestVerify verify) throws IOException {
                RequestBody body = new FormBody.Builder()
                        .add("cid", String.valueOf(countryID))
                        .add("tel", String.valueOf(tel))
                        .add("source", "main_web")
                        .add("token", verify.getToken())
                        .add("challenge", verify.getChallenge())
                        .add("validate", verify.getValidate())
                        .add("seccode", verify.getSeccode())
                        .build();
                Request req = new Request.Builder()
                        .post(body)
                        .url("https://passport.bilibili.com/x/passport-login/web/sms/send")
                        .build();
                try (Response resp = client.newCall(req).execute()) {
                    if (resp.body() != null) {
                        String str = resp.body().string();
                        JsonObject object = JsonParser.parseString(str).getAsJsonObject();
                        if (object.getAsJsonPrimitive("code").getAsInt() != 0) {
                            throw new BadRequestDataException(
                                    object.getAsJsonPrimitive("code").getAsInt(),
                                    object.getAsJsonPrimitive("message").getAsString()
                            );
                        }
                        return object.getAsJsonObject("data").getAsJsonPrimitive("captcha_key").getAsString();
                    } else {
                        throw new IOException("Empty body");
                    }
                }
            }

            public loginData loginWithSMS(String SMSToken, long tel, int countryID, int SMSCode) throws IOException {
                RequestBody body = new FormBody.Builder()
                        .add("cid", String.valueOf(countryID))
                        .add("tel", String.valueOf(tel))
                        .add("code", String.valueOf(SMSCode))
                        .add("source", "main_web")
                        .add("captcha_key", SMSToken)
                        .add("go_url", "https://www.bilibili.com")
                        .build();
                Request req = new Request.Builder()
                        .post(body)
                        .url("https://passport.bilibili.com/x/passport-login/web/login/sms")
                        .build();
                try (Response resp = client.newCall(req).execute()) {
                    if (resp.body() != null) {
                        String str = resp.body().string();
                        JsonObject object = JsonParser.parseString(str).getAsJsonObject();
                        if (object.getAsJsonPrimitive("code").getAsInt() != 0) {
                            throw new BadRequestDataException(
                                    object.getAsJsonPrimitive("code").getAsInt(),
                                    object.getAsJsonPrimitive("message").getAsString()
                            );
                        }
                        String RT = object.getAsJsonObject("data").getAsJsonPrimitive("refresh_token").getAsString();
                        long TS = object.getAsJsonObject("data").getAsJsonPrimitive("timestamp").getAsLong();

                        return new loginData(RT, simpleCookieJar.getCookieStore(), TS);
                    } else {
                        throw new IOException("Empty body");
                    }
                }
            }
        }

        public class Password {
            private keys getSaltAndKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
                Request req = new Request.Builder()
                        .url("https://passport.bilibili.com/x/passport-login/web/key")
                        .build();
                try (Response resp = client.newCall(req).execute()) {
                    if (resp.body() != null) {
                        String str = resp.body().string();
                        JsonObject object = JsonParser.parseString(str).getAsJsonObject();
                        if (object.getAsJsonPrimitive("code").getAsInt() != 0) {
                            throw new BadRequestDataException(
                                    object.getAsJsonPrimitive("code").getAsInt(),
                                    object.getAsJsonPrimitive("message").getAsString()
                            );
                        }
                        String salt = object.getAsJsonObject("data").getAsJsonPrimitive("hash").getAsString();
                        String keyS = object.getAsJsonObject("data").getAsJsonPrimitive("key").getAsString();

                        return new keys(salt,
                                (RSAPublicKey) KeyFactory.getInstance("RSA")
                                        .generatePublic(
                                                new X509EncodedKeySpec(
                                                        Base64.getDecoder().decode(keyS)
                                                )
                                        )
                        );
                    } else {
                        throw new IOException("Empty body");
                    }
                }
            }

            public loginData loginWithPassword(String username, String password, geetestVerify verify) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
                keys k = getSaltAndKey();
                Cipher cipher = Cipher.getInstance("RSA");
                cipher.init(Cipher.ENCRYPT_MODE, k.getKey());
                String encryptPw = Base64.getEncoder().encodeToString(cipher.doFinal((k.getSalt() + password).getBytes(StandardCharsets.UTF_8)));

                RequestBody body = new FormBody.Builder()
                        .add("username", username)
                        .add("password", encryptPw)
                        .add("keep", "0")
                        .add("token", verify.getToken())
                        .add("challenge", verify.getChallenge())
                        .add("validate", verify.getValidate())
                        .add("seccode", verify.getSeccode())
                        .add("go_url", "https://www.bilibili.com")
                        .add("source", "main_web")
                        .build();
                Request req = new Request.Builder()
                        .post(body)
                        .url("https://passport.bilibili.com/x/passport-login/web/login")
                        .build();
                try (Response resp = client.newCall(req).execute()) {
                    if (resp.body() != null) {
                        String str = resp.body().string();
                        JsonObject object = JsonParser.parseString(str).getAsJsonObject();
                        if (object.getAsJsonPrimitive("code").getAsInt() != 0) {
                            throw new BadRequestDataException(
                                    object.getAsJsonPrimitive("code").getAsInt(),
                                    object.getAsJsonPrimitive("message").getAsString()
                            );
                        }
                        String RT = object.getAsJsonObject("data").getAsJsonPrimitive("refresh_token").getAsString();
                        long ts = object.getAsJsonObject("data").getAsJsonPrimitive("timestamp").getAsLong();

                        return new loginData(RT, simpleCookieJar.getCookieStore(), ts);
                    } else {
                        throw new IOException("Empty body");
                    }
                }
            }

            private class keys {
                @Getter
                private final String salt;
                @Getter
                private final RSAPublicKey key;

                private keys(String salt, RSAPublicKey key) {
                    this.salt = salt;
                    this.key = key;
                }
            }
        }

        public class QR {
            public void loginWithQrLogin(Callback callback) {
                client.newCall(
                        new Request.Builder().url("https://passport.bilibili.com/x/passport-login/web/qrcode/generate?source=main-fe-header").build()
                ).enqueue(new okhttp3.Callback() {
                    @Override
                    public void onFailure(@NotNull Call call, @NotNull IOException e) {
                        callback.onFailure(e, null, -1);
                    }

                    @Override
                    public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                        if (response.body() != null) {
                            String s = response.body().string();
                            Timer timer = new Timer();
                            JsonObject object = JsonParser.parseString(s).getAsJsonObject();
                            if (object.getAsJsonPrimitive("code").getAsInt() != 0) {
                                callback.onFailure(
                                        new BadRequestDataException(
                                                object.getAsJsonPrimitive("code").getAsInt(),
                                                object.getAsJsonPrimitive("message").getAsString()
                                        )
                                        , object.getAsJsonPrimitive("message").getAsString()
                                        , object.getAsJsonPrimitive("code").getAsInt());
                            }
                            callback.onGetQRUrl(object.getAsJsonObject("data").getAsJsonPrimitive("url").getAsString());
                            String QRKey = object.getAsJsonObject("data").getAsJsonPrimitive("qrcode_key").getAsString();
                            timer.schedule(new TimerTask() {
                                private final long ts = System.currentTimeMillis();

                                @Override
                                public void run() {
                                    if ((System.currentTimeMillis() - ts) >= 180 * 1000) {
                                        cancel();
                                        return;
                                    }
                                    client.newCall(
                                            new Request.Builder()
                                                    .url(String.format("https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=%s", QRKey))
                                                    .build()
                                    ).enqueue(new okhttp3.Callback() {
                                        @Override
                                        public void onFailure(@NotNull Call call, @NotNull IOException e) {
                                            callback.onFailure(e, null, -1);
                                            cancel();
                                        }

                                        @Override
                                        public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                                            if (response.body() != null) {
                                                String s = response.body().string();
                                                JsonObject object = JsonParser.parseString(s).getAsJsonObject();
                                                JsonObject jsonObject = object.getAsJsonObject("data");
                                                int code = jsonObject.getAsJsonPrimitive("code").getAsInt();
                                                String msg = jsonObject.getAsJsonPrimitive("message").getAsString();
                                                switch (code) {
                                                    case 0:
                                                        long ts = jsonObject.getAsJsonPrimitive("timestamp").getAsLong();
                                                        String rt = jsonObject.getAsJsonPrimitive("refresh_token").getAsString();
                                                        callback.onSuccess(
                                                                new loginData(rt, simpleCookieJar.getCookieStore(), ts),
                                                                msg,
                                                                code
                                                        );
                                                        cancel();
                                                        break;
                                                    case 86101:
                                                    case 86090:
                                                        callback.onUpdate(msg, code);
                                                        break;
                                                    case 86038:
                                                        callback.onFailure(new BadRequestDataException(code, msg), msg, code);
                                                        cancel();
                                                        break;
                                                }
                                            } else {
                                                callback.onFailure(new IOException("Empty Body"), null, -1);
                                                cancel();
                                            }
                                        }
                                    });
                                }
                            }, 0, 1000L);
                        } else {
                            callback.onFailure(new IOException("Empty Body"), null, -1);
                        }
                    }
                });
            }
        }
    }

    public class Account {
        public boolean isNeedRefreshToken() throws IOException {
            Request req = new Request.Builder().url("https://passport.bilibili.com/x/passport-login/web/cookie/info").build();
            try (Response resp = client.newCall(req).execute()) {
                if (resp.body() != null) {
                    String str = resp.body().string();
                    JsonObject object = JsonParser.parseString(str).getAsJsonObject();
                    if (object.getAsJsonPrimitive("code").getAsInt() != 0) {
                        throw new BadRequestDataException(
                                object.getAsJsonPrimitive("code").getAsInt(),
                                object.getAsJsonPrimitive("message").getAsString()
                        );
                    }
                    object = object.getAsJsonObject("data");
                    return object.getAsJsonPrimitive("refresh").getAsBoolean();
                } else {
                    throw new IOException("Empty body");
                }
            }
        }

        public String getCsrfToken(String correspondPath) throws IOException {
            Request req = new Request.Builder().url(String.format("https://www.bilibili.com/correspond/1/%s", correspondPath)).build();
            try (Response resp = client.newCall(req).execute()) {
                if (resp.body() != null) {
                    Document docDesc = Jsoup.parse(resp.body().string());
                    Element e = docDesc.getElementById("1-name");
                    if (e != null) {
                        return e.text();
                    }
                    return null;
                } else {
                    throw new IOException("Empty body");
                }
            }
        }

        /**
         * @param csrf         Cookie -> bili_jct
         * @param refreshCsrf  Csrf Token
         * @param refreshToken Refresh Token
         * @return New Cookie and refresh token
         */
        public loginData refreshCookie(String csrf, String refreshCsrf, String refreshToken) throws IOException {
            RequestBody body = new FormBody.Builder()
                    .add("csrf", csrf)
                    .add("refresh_csrf", refreshCsrf)
                    .add("source", "main_web")
                    .add("refresh_token", refreshToken)
                    .build();
            Request req = new Request.Builder()
                    .post(body)
                    .url("https://passport.bilibili.com/x/passport-login/web/cookie/refresh")
                    .build();
            try (Response resp = client.newCall(req).execute()) {
                if (resp.body() != null) {
                    String str = resp.body().string();
                    JsonObject object = JsonParser.parseString(str).getAsJsonObject();
                    if (object.getAsJsonPrimitive("code").getAsInt() != 0) {
                        throw new BadRequestDataException(
                                object.getAsJsonPrimitive("code").getAsInt(),
                                object.getAsJsonPrimitive("message").getAsString()
                        );
                    }
                    return new loginData(
                            object.getAsJsonObject("data").getAsJsonPrimitive("refresh_token").getAsString(),
                            simpleCookieJar.getCookieStore(),
                            System.currentTimeMillis()
                    );
                } else {
                    throw new IOException("Empty body");
                }
            }
        }

        /**
         * @param csrf            Cookie -> bili_jct
         * @param oldRefreshToken Old Refresh Token
         */
        public void setOldCookieInvalid(String csrf, String oldRefreshToken) throws IOException {
            RequestBody body = new FormBody.Builder()
                    .add("csrf", csrf)
                    .add("source", "main_web")
                    .add("refresh_token", oldRefreshToken)
                    .build();
            Request req = new Request.Builder()
                    .post(body)
                    .url("https://passport.bilibili.com/x/passport-login/web/confirm/refresh")
                    .build();
            try (Response resp = client.newCall(req).execute()) {
                if (resp.body() != null) {
                    String str = resp.body().string();
                    JsonObject object = JsonParser.parseString(str).getAsJsonObject();
                    if (object.getAsJsonPrimitive("code").getAsInt() != 0) {
                        throw new BadRequestDataException(
                                object.getAsJsonPrimitive("code").getAsInt(),
                                object.getAsJsonPrimitive("message").getAsString()
                        );
                    }
                } else {
                    throw new IOException("Empty body");
                }
            }
        }
    }
}
