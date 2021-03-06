/*******************************************************************************
 * Copyright 2013 alladin-IT OG
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package at.alladin.rmbt.android.help;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import at.alladin.openrmbt.android.R;
import at.alladin.rmbt.android.util.ConfigHelper;

/**
 * 
 * @author
 * 
 */
public class RMBTHelpFragment extends Fragment
{
    
    // private static final String DEBUG_TAG = "RMBTHelpFragment";
    
    /**
	 * 
	 */
    public static final String ARG_URL = "url";
    
    /**
	 * 
	 */
    private WebView webview;
    
    /**
	 * 
	 */
    private FragmentActivity activity;
    
    /**
	 * 
	 */
    private boolean encryption;
    
    /**
	 * 
	 */
    private String url;
    
    /**
	 * 
	 */
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
    {
        
        super.onCreateView(inflater, container, savedInstanceState);
        
        final Bundle args = getArguments();
        
        url = args.getString(ARG_URL);
        
        if (url.length() == 0)
            url = this.getString(R.string.url_help);
        
        activity = getActivity();
        
        webview = new WebView(activity)
        {
            @Override
            public boolean onKeyDown(final int keyCode, final KeyEvent event)
            {
                if (keyCode == KeyEvent.KEYCODE_BACK && canGoBack())
                {
                    goBack();
                    return true;
                }
                return super.onKeyDown(keyCode, event);
            }
        };
        
        encryption = ConfigHelper.isControlSeverSSL(activity);
        
        final String protocol = encryption ? "https" : "http";
        
        /* JavaScript must be enabled if you want it to work, obviously */
        // webview.getSettings().setJavaScriptEnabled(true);
        
        webview.setWebViewClient(new WebViewClient()
        {
            @Override
            public void onReceivedError(final WebView view, final int errorCode, final String description,
                    final String failingUrl)
            {
                super.onReceivedError(view, errorCode, description, failingUrl);
                webview.loadUrl("file:///android_res/raw/error.html");
            }
        });
        
        webview.loadUrl(protocol + "://" + url);
        
        return webview;
    }
    
}
