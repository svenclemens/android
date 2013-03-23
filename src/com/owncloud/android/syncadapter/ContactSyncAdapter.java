/* ownCloud Android client application
 *   Copyright (C) 2012 Bartek Przybylski
 *   Copyright (C) 2012-2013 ownCloud Inc.
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 2 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

/*
 * @author zerginator 
 */
package com.owncloud.android.syncadapter;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import com.owncloud.android.AccountUtils;
import com.owncloud.android.authenticator.AccountAuthenticator;
import com.owncloud.android.network.AdvancedSslSocketFactory;
import com.owncloud.android.network.AdvancedX509TrustManager;
import com.owncloud.android.network.OwnCloudClientUtils;
import com.owncloud.android.utils.OwnCloudVersion;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.ContactsContract;

public class ContactSyncAdapter extends AbstractOwnCloudSyncAdapter {
    private String mAddrBookUri;

    public ContactSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mAddrBookUri = null;
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
            ContentProviderClient provider, SyncResult syncResult) {
        setAccount(account);
        setContentProvider(provider);
        Cursor c = getLocalContacts(false);
        if (c.moveToFirst()) {
            do {
                
                String url = getAddressBookUri();
                List<String> vcfList = getVCFList(url);
                
                
               
            } while (c.moveToNext());
        }

    }
    
    private List<String> getVCFList(String adressbookURL) {
       
        InputStream inputStream = null;
        
        List<String> vcfList = new ArrayList<String>(); 
//        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
//        StrictMode.setThreadPolicy(policy); 
        try {
            OwnCloudClientUtils.registerAdvancedSslContext(true, getContext());
        } catch (GeneralSecurityException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
//        try {
//            AdvancedSslSocketFactory sslFactory = OwnCloudClientUtils.getAdvancedSslSocketFactory(getContext());
//            if (sslFactory != null) {
//               Socket socket =  sslFactory.createSocket("testcloud.haga.me", 443);
//               inputStream = socket.getInputStream();
//                
//            }
////            HostnameVerifier hostnameVerifier = sslFactory.getHostNameVerifier();
////            hostnameVerifier.
//            
//        } catch (GeneralSecurityException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                
                    Document doc = db.parse(inputStream);
                    Element content = doc.getElementById("content");
                    NodeList links = content.getElementsByTagName("a");
                    for (int i=0; i<links.getLength(); i++) {
                        Element element = (Element) links.item(i);
                        if (element.hasAttribute("href")) {
                            element.getAttribute("href");
                            String fileURL = element.getTextContent();
                            if (isVCFfile(fileURL)) {
                                vcfList.add(fileURL);
                            }
                        }
                    }
                    
                   return vcfList;
               
        } catch (MalformedURLException e) {
                e.printStackTrace();  
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    private boolean isVCFfile(String fileURL) {
        boolean result = false;
        String[] fileSplited = fileURL.split("\\.");
        if (fileSplited[fileSplited.length-1].equals("vcf")) {
               result = true;
        }
        return result;
    }
    
    
    private void compareVCards(String uri_path, Cursor c) {
        String lookup = c.getString(c
                .getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY));
        
        String uri = uri_path+ lookup + ".vcf";
        FileInputStream f;
        try {
            f = getContactVcard(lookup);
            HttpPut query = new HttpPut(uri);
            byte[] b = new byte[f.available()];
            f.read(b);
            query.setEntity(new ByteArrayEntity(b));
            fireRawRequest(query);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        } catch (OperationCanceledException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (AuthenticatorException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private String getAddressBookUri() {
        if (mAddrBookUri != null)
            return mAddrBookUri;

        AccountManager mAccountManager = getAccountManager();
        
        OwnCloudVersion ocv = new OwnCloudVersion(mAccountManager.getUserData(getAccount(),
                              AccountAuthenticator.KEY_OC_VERSION));
  
        String base_url = mAccountManager.getUserData(getAccount(),AccountAuthenticator.KEY_OC_BASE_URL);
        String carddav_path = AccountUtils.getCarddavPath(ocv);
        String oc_uri_path =  base_url + carddav_path + "/addressbooks/" + getAccount().name.substring(0,getAccount().name.lastIndexOf('@')) + "/contacts/";
        
        
//        Uri oc_uri = Uri.parse(oc_uri_path);
        mAddrBookUri = oc_uri_path;
        return oc_uri_path;
    }

    private FileInputStream getContactVcard(String lookupKey)
            throws IOException {
        Uri uri = Uri.withAppendedPath(
                ContactsContract.Contacts.CONTENT_VCARD_URI, lookupKey);
        AssetFileDescriptor fd = getContext().getContentResolver()
                .openAssetFileDescriptor(uri, "r");
        return fd.createInputStream();
    }

    private Cursor getLocalContacts(boolean include_hidden_contacts) {
        return getContext().getContentResolver().query(
                ContactsContract.Contacts.CONTENT_URI,
                new String[] { ContactsContract.Contacts._ID,
                        ContactsContract.Contacts.LOOKUP_KEY },
                ContactsContract.Contacts.IN_VISIBLE_GROUP + " = ?",
                new String[] { (include_hidden_contacts ? "0" : "1") },
                ContactsContract.Contacts._ID + " DESC");
    }

}
