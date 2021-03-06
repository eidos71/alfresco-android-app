/*******************************************************************************
 * Copyright (C) 2005-2013 Alfresco Software Limited.
 * 
 * This file is part of Alfresco Mobile for Android.
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
package org.alfresco.mobile.android.application.activity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.alfresco.mobile.android.api.model.Document;
import org.alfresco.mobile.android.application.R;
import org.alfresco.mobile.android.application.accounts.AccountManager;
import org.alfresco.mobile.android.application.fragments.DisplayUtils;
import org.alfresco.mobile.android.application.fragments.FragmentDisplayer;
import org.alfresco.mobile.android.application.fragments.WaitingDialogFragment;
import org.alfresco.mobile.android.application.fragments.browser.onPickDocumentFragment;
import org.alfresco.mobile.android.application.fragments.operations.OperationsFragment;
import org.alfresco.mobile.android.application.fragments.workflow.CreateTaskDocumentPickerFragment;
import org.alfresco.mobile.android.application.fragments.workflow.CreateTaskFragment;
import org.alfresco.mobile.android.application.fragments.workflow.CreateTaskTypePickerFragment;
import org.alfresco.mobile.android.application.intent.IntentIntegrator;
import org.alfresco.mobile.android.application.preferences.GeneralPreferences;
import org.alfresco.mobile.android.application.preferences.PasscodePreferences;
import org.alfresco.mobile.android.application.security.PassCodeActivity;
import org.alfresco.mobile.android.application.utils.UIUtils;

import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * @author Jean Marie Pascal
 */
public class PrivateDialogActivity extends BaseActivity
{
    private static final String TAG = PrivateDialogActivity.class.getName();

    private boolean activateCheckPasscode = false;

    private boolean doubleBackToExitPressedOnce = false;

    // ///////////////////////////////////////////////////////////////////////////
    // LIFECYCLE
    // ///////////////////////////////////////////////////////////////////////////
    /** Called when the activity is first created. */
    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        activateCheckPasscode = false;

        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_ACTION_BAR);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND, WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        android.view.WindowManager.LayoutParams params = getWindow().getAttributes();

        int[] values = UIUtils.getScreenDimension(this);
        int height = values[1];
        int width = values[0];

        params.height = (int) Math.round(height * 0.9);
        params.width = (int) Math
                .round(width
                        * (Float.parseFloat(getResources().getString(android.R.dimen.dialog_min_width_minor).replace(
                                "%", "")) * 0.01));

        params.alpha = 1.0f;
        params.dimAmount = 0.5f;
        getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);

        setContentView(R.layout.app_left_panel);

        if (getIntent().hasExtra(IntentIntegrator.EXTRA_ACCOUNT_ID))
        {
            currentAccount = AccountManager.retrieveAccount(this,
                    getIntent().getLongExtra(IntentIntegrator.EXTRA_ACCOUNT_ID, 1));
        }

        String action = getIntent().getAction();
        if (IntentIntegrator.ACTION_DISPLAY_SETTINGS.equals(action))
        {
            Fragment f = new GeneralPreferences();
            FragmentDisplayer.replaceFragment(this, f, DisplayUtils.getLeftFragmentId(this), GeneralPreferences.TAG,
                    false, false);
            return;
        }

        if (IntentIntegrator.ACTION_DISPLAY_OPERATIONS.equals(action))
        {
            Fragment f = new OperationsFragment();
            FragmentDisplayer.replaceFragment(this, f, DisplayUtils.getLeftFragmentId(this), OperationsFragment.TAG,
                    false, false);
            return;
        }

        if (IntentIntegrator.ACTION_START_PROCESS.equals(action)
                && getFragment(CreateTaskTypePickerFragment.TAG) == null)
        {
            List<Document> docs = new ArrayList<Document>();
            if (getIntent().getExtras() != null && getIntent().getExtras().containsKey(IntentIntegrator.EXTRA_DOCUMENT))
            {
                docs.add((Document) getIntent().getExtras().get(IntentIntegrator.EXTRA_DOCUMENT));
                getIntent().removeExtra(IntentIntegrator.EXTRA_DOCUMENT);
            }
            else if (getIntent().getExtras() != null
                    && getIntent().getExtras().containsKey(IntentIntegrator.EXTRA_DOCUMENTS))
            {
                docs.addAll((Collection<? extends Document>) getIntent().getExtras().get(
                        IntentIntegrator.EXTRA_DOCUMENTS));
                getIntent().removeExtra(IntentIntegrator.EXTRA_DOCUMENTS);
            }

            Fragment f = docs.isEmpty() ? new CreateTaskTypePickerFragment() : CreateTaskTypePickerFragment
                    .newInstance(docs);
            FragmentDisplayer.replaceFragment(this, f, DisplayUtils.getLeftFragmentId(this),
                    CreateTaskTypePickerFragment.TAG, false, false);
            return;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PassCodeActivity.REQUEST_CODE_PASSCODE)
        {
            if (resultCode == RESULT_CANCELED)
            {
                finish();
            }
            else
            {
                activateCheckPasscode = true;
            }
        }
    }

    @Override
    protected void onStart()
    {
        getActionBar().setDisplayHomeAsUpEnabled(true);
        if (receiver == null)
        {
            receiver = new PrivateDialogActivityReceiver();
            IntentFilter filters = new IntentFilter(IntentIntegrator.ACTION_DECRYPT_ALL_COMPLETED);
            filters.addAction(IntentIntegrator.ACTION_ENCRYPT_ALL_COMPLETED);
            broadcastManager.registerReceiver(receiver, filters);
        }
        super.onStart();
        PassCodeActivity.requestUserPasscode(this);
        activateCheckPasscode = PasscodePreferences.hasPasscodeEnable(this);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if (!activateCheckPasscode)
        {
            PasscodePreferences.updateLastActivityDisplay(this);
        }
    }

    @Override
    protected void onStop()
    {
        if (receiver != null)
        {
            broadcastManager.unregisterReceiver(receiver);
        }
        super.onStop();
    }

    // ///////////////////////////////////////////////////////////////////////////
    // MENU
    // ///////////////////////////////////////////////////////////////////////////
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case android.R.id.home:
                if (getIntent() != null && IntentIntegrator.ACTION_PICK_FILE.equals(getIntent().getAction()))
                {
                    finish();
                }
                else
                {
                    Intent i = new Intent(this, MainActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(i);
                    finish();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // ///////////////////////////////////////////////////////////////////////////
    // PUBLIC
    // ///////////////////////////////////////////////////////////////////////////
    public onPickDocumentFragment getOnPickDocumentFragment()
    {
        return (onPickDocumentFragment) getFragmentManager().findFragmentByTag(CreateTaskFragment.TAG);
    }

    public void doCancel(View v)
    {
        getFragmentManager().popBackStackImmediate(CreateTaskDocumentPickerFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }
    // ////////////////////////////////////////////////////////
    // BROADCAST RECEIVER
    // ///////////////////////////////////////////////////////
    private class PrivateDialogActivityReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            Log.d(TAG, intent.getAction());

            if (IntentIntegrator.ACTION_DECRYPT_ALL_COMPLETED.equals(intent.getAction())
                    || IntentIntegrator.ACTION_ENCRYPT_ALL_COMPLETED.equals(intent.getAction()))
            {
                if (getFragment(WaitingDialogFragment.TAG) != null)
                {
                    ((DialogFragment) getFragment(WaitingDialogFragment.TAG)).dismiss();
                }
                return;
            }

        }
    }

    @Override
    public void onBackPressed()
    {
        if (getFragment(WaitingDialogFragment.TAG) != null)
        {
            if (doubleBackToExitPressedOnce)
            {
                ((DialogFragment) getFragment(WaitingDialogFragment.TAG)).dismiss();
                return;
            }
            this.doubleBackToExitPressedOnce = true;
            Toast.makeText(this, "Please click BACK again to exit", Toast.LENGTH_SHORT).show();
            new Handler().postDelayed(new Runnable()
            {
                @Override
                public void run()
                {
                    doubleBackToExitPressedOnce = false;
                }
            }, 2000);
        }
        else
        {
            super.onBackPressed();
        }
    }
}
