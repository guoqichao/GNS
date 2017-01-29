
package edu.umass.cs.gnsclient.client.util;

import edu.umass.cs.gnsclient.client.GNSClientCommands;
import edu.umass.cs.gnscommon.GNSProtocol;
import edu.umass.cs.gnscommon.exceptions.client.EncryptionException;
import edu.umass.cs.gnscommon.utils.Base64;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;


public class GuidEntry extends BasicGuidEntry implements Serializable {

  private static final long serialVersionUID = 2326392043474125897L;
  private final PrivateKey privateKey;


  public GuidEntry(String entityName, String guid, PublicKey publicKey,
          PrivateKey privateKey) throws EncryptionException {
    super(entityName, guid, publicKey);
    this.privateKey = privateKey;
  }


  public GuidEntry(ObjectInputStream s) throws IOException, EncryptionException {
    //readObject(s);
	  super(s.readUTF(), s.readUTF(), s.readUTF());
	  this.privateKey = generatePrivateKey(s.readUTF());
  }


  public PrivateKey getPrivateKey() {
    return privateKey;
  }


  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof GuidEntry)) {
      return false;
    }
    GuidEntry other = (GuidEntry) o;
    if (entityName == null && other.getEntityName() != null) {
      return false;
    }
    if (entityName != null && !entityName.equals(other.getEntityName())) {
      return false;
    }
    if (!publicKey.equals(other.getPublicKey())) {
      return false;
    }
    if (privateKey == null && other.getPrivateKey() != null) {
      return false;
    }
    if (privateKey == null) {
      return true;
    } else {
      return privateKey.equals(other.privateKey);
    }
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 17 * hash + (this.entityName != null ? this.entityName.hashCode() : 0);
    hash = 17 * hash + (this.guid != null ? this.guid.hashCode() : 0);
    hash = 17 * hash + (this.publicKey != null ? this.publicKey.hashCode() : 0);
    hash = 17 * hash + (this.privateKey != null ? this.privateKey.hashCode() : 0);
    return hash;
  }


  public void writeObject(ObjectOutputStream s) throws IOException {
    s.writeUTF(entityName);
    s.writeUTF(guid);
    s.writeUTF(Base64.encodeToString(publicKey.getEncoded(), true));
    s.writeUTF(Base64.encodeToString(privateKey.getEncoded(), true));
  }

  // arun: removed this method to make all fields final
//  private void readObject(ObjectInputStream s) throws IOException {
//    entityName = s.readUTF();
//    guid = s.readUTF();
//    KeyPair keypair;
//    try {
//      keypair = generateKeyPair(s.readUTF(), s.readUTF());
//      publicKey = keypair.getPublic();
//      privateKey = keypair.getPrivate();
//    } catch (EncryptionException e) {
//      throw new IOException(e);
//    }
//  }

  private static PrivateKey generatePrivateKey(String encodedPrivate)
          throws EncryptionException {
    byte[] encodedPrivateKey = Base64.decode(encodedPrivate);

    try {
      return KeyFactory.getInstance(GNSProtocol.RSA_ALGORITHM.toString()).generatePrivate(new PKCS8EncodedKeySpec(
              encodedPrivateKey));
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new EncryptionException("Failed to generate keypair", e);
    }
  }


  private static KeyPair generateKeyPair(String encodedPublic, String encodedPrivate)
          throws EncryptionException {
    byte[] encodedPublicKey = Base64.decode(encodedPublic);
    byte[] encodedPrivateKey = Base64.decode(encodedPrivate);

    try {
      KeyFactory keyFactory = KeyFactory.getInstance(GNSProtocol.RSA_ALGORITHM.toString());
      X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(
              encodedPublicKey);
      PublicKey thePublicKey = keyFactory.generatePublic(publicKeySpec);

      PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(
              encodedPrivateKey);
      PrivateKey thePrivateKey = keyFactory.generatePrivate(privateKeySpec);
      return new KeyPair(thePublicKey, thePrivateKey);
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new EncryptionException("Failed to generate keypair", e);
    }
  }

  // Test code

public static void main(String[] args) throws IOException, Exception {
    String name = "testGuid@gigapaxos.net";
    String password = "123";
    String file_name = "guid";

    GNSClientCommands client = new GNSClientCommands();

    GuidEntry guidEntry = client.accountGuidCreate(name, password);

    FileOutputStream fos = new FileOutputStream(file_name);
    ObjectOutputStream os = new ObjectOutputStream(fos);
    guidEntry.writeObject(os);
    os.flush(); // Important to flush

    FileInputStream fis = new FileInputStream(file_name);
    ObjectInputStream ois = new ObjectInputStream(fis);

    GuidEntry newEntry = new GuidEntry(ois);
    System.out.println(newEntry);
  }

}
