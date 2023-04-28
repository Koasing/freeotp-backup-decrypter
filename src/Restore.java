import com.google.gson.Gson;

import org.apache.commons.codec.binary.Base32;
import org.fedorahosted.freeotp.encryptor.EncryptedKey;
import org.fedorahosted.freeotp.encryptor.MasterKey;
import org.fedorahosted.freeotp.Token;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

public class Restore {

    public class RestoredData {
        public SecretKey key;
        public Token token;
        public String uuid;
    }

    public class BadPasswordException extends Exception {
        public BadPasswordException() {
            super("Invalid password");
        }
    }

    private static final String MASTER = "masterKey";
    private Map<String, ?> mBackups;

    public List<RestoredData> restore(String pwd) throws GeneralSecurityException,
            IOException, JSONException, BadPasswordException  {

        ArrayList<RestoredData> tokensList = new ArrayList<>();

        String s;
        if(mBackups.containsKey(MASTER)) {
            s = (String) mBackups.get(MASTER);
        }
        else {
            s = new Gson().toJson(MasterKey.generate(pwd));
        }

        MasterKey mk = new Gson().fromJson(s, MasterKey.class);
        SecretKey sk;
        try {
            sk = mk.decrypt(pwd);
        } catch (Exception e) {
            e.printStackTrace();
            throw new BadPasswordException();
        }

        for (Map.Entry<String, ?> item : mBackups.entrySet()) {
            JSONObject obj;
            String uuid = item.getKey();
            Object v = item.getValue();
            RestoredData bkp = new RestoredData();

            System.out.println(String.format("Found [%s] in backup", uuid));
            if (uuid.equals(MASTER) || item.getKey().contains("-token")) {
                System.out.println(String.format("Skipping [%s]", uuid));
                continue;
            }

            if (!(v instanceof String)) {
                System.out.println(String.format("[%s] Not a string", uuid));
                continue;
            }

            try {
                obj = new JSONObject(v.toString());
            } catch (JSONException e) {
                // Invalid JSON backup data
                System.out.println("Exception!");
                continue;
            }

            // Retrieve encrypted backup data from shared preferences
            String tokenData = (String)mBackups.get(uuid.concat("-token"));
            EncryptedKey ekKey = new Gson().fromJson(obj.getString("key"), EncryptedKey.class);

            // Decrypt the token
            SecretKey skKey = ekKey.decrypt(sk);

            // Deserialize token
            Token token = Token.deserialize(tokenData);

            bkp.key = skKey;
            bkp.token = token;
            bkp.uuid = uuid;
            System.out.println(String.format("Added [%s] token to backup list", uuid));
            tokensList.add(bkp);
        }

        return tokensList;
    }

    public void restoreBackupFromExternal(String filename, String pwd) {
        ObjectInputStream input = null;
        Base32 base32 = new Base32();

        try {
            InputStream inputStream = new FileInputStream(filename);
            input = new ObjectInputStream(inputStream);

            Map<String, ?> entries = (Map<String, ?>) input.readObject();

            // Print encrypted data
            System.out.println("----- ENCRYPTED DATA -----");
            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                Object v = entry.getValue();
                String key = entry.getKey();

                System.out.print(key);
                System.out.print(" -> ");

                if (v instanceof Boolean)
                    System.out.println((Boolean)v);
                else if (v instanceof Float)
                    System.out.println((Float)v);
                else if (v instanceof Integer)
                    System.out.println((Integer)v);
                else if (v instanceof Long)
                    System.out.println((Long)v);
                else if (v instanceof String)
                    System.out.println((String)v);
            }

            this.mBackups = entries;

            System.out.println("----- DECRYPT -----");
            List<RestoredData> restored = restore(pwd);

            System.out.println("----- DECRYPTED DATA -----");
            for (RestoredData r : restored) {
                System.out.println(String.format("UUID [%s]", r.uuid));
                System.out.println(String.format("Account [%s], Issued by [%s]", r.token.getLabel(), r.token.getIssuer()));
                System.out.println(String.format("Current code: [%s]", r.token.getCode(r.key).getCode()));
                System.out.println(String.format(" OTP SECRET : [%s]", base32.encodeToString(r.key.getEncoded())));
            }

        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Exception!");
        } catch (BadPasswordException | GeneralSecurityException e) {
            e.printStackTrace();
        } finally {
            try {
                if (input != null) {
                    input.close();
                }
            } catch (IOException ex) {
                System.out.println("Exception!");
            }
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Password argument required.");
            return;
        }

        Restore r = new Restore();
        r.restoreBackupFromExternal("externalBackup.xml", args[0]);
    }
}
