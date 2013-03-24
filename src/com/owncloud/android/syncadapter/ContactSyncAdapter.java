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
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.params.CoreProtocolPNames;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;

import com.owncloud.android.AccountUtils;
import com.owncloud.android.authenticator.AccountAuthenticator;
import com.owncloud.android.network.OwnCloudClientUtils;
import com.owncloud.android.utils.OwnCloudVersion;

public class ContactSyncAdapter extends AbstractOwnCloudSyncAdapter {
    
    private static final String TAG = "ContactSyncAdapter";
    private static final String USER_AGENT = "Android-ownCloud";
    private String mAddrBookUri;

    public ContactSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        Log.d(TAG, "constructor reached");
        mAddrBookUri = null;
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
        ContentProviderClient provider, SyncResult syncResult) {
//        android.os.Debug.waitForDebugger();
        setAccount(account);
        setContentProvider(provider);
        String url = getAddressBookUri();
        List<String> vcfList = getVCFList(url,account);
        if (vcfList != null && vcfList.size() != 0) {
            Log.d(TAG, "fertige VCFLISTE !!" + vcfList.toString());
        }
        
        Cursor c = getLocalContacts(false);
        if (c.moveToFirst()) {
            do {
                
                
                //TODO upload local contact
                
                
            } while (c.moveToNext());
        }
    }
    
    private List<String> getVCFList(String adressbookURL,Account account) {
        List<String> vcfList = new ArrayList<String>(); 
        URL url = null; 
        Document doc = null;
        String username = account.name.substring(0, account.name.lastIndexOf('@'));
        String password = AccountManager.get(getContext()).getPassword(account);
        HttpClient httpClient = new HttpClient();
        httpClient.getParams().setAuthenticationPreemptive(true);
        httpClient.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username,password));
        httpClient.getParams().setParameter(HttpMethodParams.USER_AGENT, USER_AGENT);
        httpClient.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
        try {
            url = new URL(adressbookURL);
            doc = OwnCloudClientUtils.getContactInputStream(getContext(), url, httpClient);  
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (doc != null) { 
                //Leave this to check the outputstream
                @SuppressWarnings("unused")
                Elements content = doc.getAllElements();
//                    Log.d(TAG,content.text());
                
                Elements links = doc.getElementsByTag("a");
                for (Element link : links) {
                  @SuppressWarnings("unused")
                  String linkHref = link.attr("href");
                  String linkText = link.text();
                  if(isVCFfile(linkText)) {
                      vcfList.add(linkText);
                  }
//                  Log.d(TAG, linkText);
                }
            }
        return vcfList;
    }

    private boolean isVCFfile(String fileURL) {
        boolean result = false;
        if (fileURL != null && fileURL.length() != 0) {
            String[] fileSplited = fileURL.split("\\.");
            if (fileSplited != null && fileSplited.length != 0) {
                if (fileSplited[fileSplited.length-1].equals("vcf")) {
                       result = true;
                }
            }
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
            e.printStackTrace();
        } catch (AuthenticatorException e) {
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



