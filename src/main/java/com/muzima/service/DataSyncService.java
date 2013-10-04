package com.muzima.service;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;

import com.muzima.BroadcastListenerActivity;
import com.muzima.MuzimaApplication;
import com.muzima.R;
import com.muzima.utils.Constants;
import com.muzima.view.cohort.AllCohortsListFragment;
import com.muzima.view.forms.AllAvailableFormsListFragment;

import java.util.Date;

import static com.muzima.utils.Constants.DataSyncServiceConstants.*;

public class DataSyncService extends IntentService {

    private static final int MUZIMA_NOTIFICATION = 0;
    private static final String TAG = "DataSyncService";
    private final String notificationServiceRunning = "Muzima Sync Service Running";
    private final String notificationServiceFinished = "Muzima Sync Service Finished";
    private String notificationMsg;
    private DownloadService downloadService;

    public DataSyncService() {
        super("DataSyncService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        downloadService = new DownloadService(((MuzimaApplication) getApplication()));
        updateNotificationMsg("Sync service started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        int syncType = intent.getIntExtra(SYNC_TYPE, -1);
        Intent broadcastIntent = new Intent();
        String[] credentials = intent.getStringArrayExtra(CREDENTIALS);
        broadcastIntent.setAction(BroadcastListenerActivity.MESSAGE_SENT_ACTION);
        broadcastIntent.putExtra(Constants.DataSyncServiceConstants.SYNC_TYPE, syncType);

        switch (syncType) {
            case SYNC_FORMS:
                updateNotificationMsg("Downloading Forms Metadata");
                if (authenticationSuccessful(credentials, broadcastIntent)) {
                    int[] result = downloadService.downloadForms();
                    String msg = "Downloaded " + result[1] + " forms";
                    prepareBroadcastMsg(broadcastIntent, result, msg);
                    saveFormsSyncTime(result);
                }
                break;
            case SYNC_TEMPLATES:
                String[] formIds = intent.getStringArrayExtra(FROM_IDS);
                updateNotificationMsg("Downloading Forms Template for " + formIds.length + " forms");
                if (authenticationSuccessful(credentials, broadcastIntent)) {
                    int[] result = downloadService.downloadFormTemplates(formIds);
                    String msg = "Downloaded " + result[1] + " form templates";
                    prepareBroadcastMsg(broadcastIntent, result, msg);
                }
                break;
            case SYNC_COHORTS:
                updateNotificationMsg("Downloading Cohorts");
                if (authenticationSuccessful(credentials, broadcastIntent)) {
                    int[] result = downloadService.downloadCohorts();
                    String msg = "Downloaded " + result[1] + " cohorts";
                    prepareBroadcastMsg(broadcastIntent, result, msg);
                    saveCohortsSyncTime(result);
                }
                break;
            case SYNC_PATIENTS:
                String[] cohortIds = intent.getStringArrayExtra(COHORT_IDS);
                updateNotificationMsg("Downloading Patients");
                if(authenticationSuccessful(credentials, broadcastIntent)){
                    int[] resultForPatients = downloadService.downloadPatientsForCohorts(cohortIds);
                    String msgForPatients = "Downloaded " + resultForPatients[1] + " patients";
                    prepareBroadcastMsg(broadcastIntent, resultForPatients, msgForPatients);
                    if (resultForPatients[0] == SyncStatusConstants.SUCCESS) {
                        broadcastIntent.putExtra(Constants.DataSyncServiceConstants.DOWNLOAD_COUNT_SECONDARY, resultForPatients[2]);
                    }
                    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);

                    int[] resultForObservations = downloadService.downloadObservationsForPatients(cohortIds);
                    String msgForObservations = "Downloaded " + resultForObservations[1] + " observations";
                    prepareBroadcastMsg(broadcastIntent, resultForObservations, msgForObservations);
                    broadcastIntent.putExtra(Constants.DataSyncServiceConstants.SYNC_TYPE, SYNC_OBSERVATIONS);

                    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);

                    int[] resultForEncounters = downloadService.downloadEncountersForPatients(cohortIds);
                    String msgForEncounters = "Downloaded " + resultForEncounters[1] + " encounters";
                    prepareBroadcastMsg(broadcastIntent, resultForEncounters, msgForEncounters);
                    broadcastIntent.putExtra(Constants.DataSyncServiceConstants.SYNC_TYPE, SYNC_ENCOUNTERS);
                }
                break;
            default:
                break;
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
    }

    private void prepareBroadcastMsg(Intent broadcastIntent, int[] result, String msg) {
        broadcastIntent.putExtra(Constants.DataSyncServiceConstants.SYNC_STATUS, result[0]);
        if (result[0] == SyncStatusConstants.SUCCESS) {
            broadcastIntent.putExtra(Constants.DataSyncServiceConstants.DOWNLOAD_COUNT_PRIMARY, result[1]);
            updateNotificationMsg(msg);
        }
    }

    private void saveFormsSyncTime(int[] result) {
        if (result[0] == SyncStatusConstants.SUCCESS) {
            SharedPreferences pref = getSharedPreferences(Constants.SYNC_PREF, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = pref.edit();
            Date date = new Date();
            editor.putLong(AllAvailableFormsListFragment.FORMS_METADATA_LAST_SYNCED_TIME, date.getTime());
            editor.commit();
        }
    }

    private void saveCohortsSyncTime(int[] result) {
        if (result[0] == SyncStatusConstants.SUCCESS) {
            SharedPreferences pref = getSharedPreferences(Constants.SYNC_PREF, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = pref.edit();
            Date date = new Date();
            editor.putLong(AllCohortsListFragment.COHORTS_LAST_SYNCED_TIME, date.getTime());
            editor.commit();
        }
    }

    private boolean authenticationSuccessful(String[] credentials, Intent broadcastIntent) {
        int authenticationStatus = downloadService.authenticate(credentials);
        if (authenticationStatus != SyncStatusConstants.AUTHENTICATION_SUCCESS) {
            broadcastIntent.putExtra(Constants.DataSyncServiceConstants.SYNC_STATUS, authenticationStatus);
            return false;
        }
        return true;
    }

    @Override
    public void onDestroy() {
        showNotification(notificationServiceFinished, notificationMsg);
        super.onDestroy();
    }

    private void showNotification(String title, String msg) {
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_launcher_logo)
                        .setContentTitle(title)
                        .setContentText(msg);
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(MUZIMA_NOTIFICATION, mBuilder.getNotification());
    }


    private void updateNotificationMsg(String msg) {
        notificationMsg = msg;
        showNotification(notificationServiceRunning, notificationMsg);
    }
}