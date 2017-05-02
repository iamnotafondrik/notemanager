package com.iamnotafondrik.notesmanager;

import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DebugMenuActivity extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_menu);
        int[] buttonIds = {R.id.debug_copy_db_to_sd, R.id.debug_remove_db, R.id.debug_remove_db_cloud, R.id.debug_remove_db_cloud,
                                R.id.debug_reset_sp};
        for (int buttonId: buttonIds) {
            findButton(buttonId);
        }
    }

    private void findButton (int buttonId) {
        Button button = (Button) findViewById(buttonId);
        if (button != null) {
            button.setOnClickListener(this);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.debug_copy_db_to_sd:
                try {
                    copyDatabaseToSdcard ();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.debug_remove_db:
                removeDatabase();
                break;
            case R.id.debug_remove_db_cloud:
                removeDatabaseFromCloudStorage();
                break;
            case R.id.debug_reset_sp:
                resetSharedPreferences ();
                break;
        }
    }

    private void copyDatabaseToSdcard () throws IOException{
        File dbFile = new File(getDatabasePath(DBHelper.DATABASE_NAME).getAbsolutePath());
        File dbFileCopy = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/NotesManager/NoteManager.db");

        InputStream in = new FileInputStream(dbFile);
        OutputStream out = new FileOutputStream(dbFileCopy);

        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    private void removeDatabase () {
        File dbFile = new File(getDatabasePath(DBHelper.DATABASE_NAME).getAbsolutePath());
        if (dbFile.exists()) {
            if (dbFile.delete()) {
                Toast.makeText(this, "Database removed", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Database NOT removed", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Database NOT found", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeDatabaseFromCloudStorage () {
        FirebaseStorageHelper firebaseStorageHelper = new FirebaseStorageHelper(this);
        firebaseStorageHelper.removeDatabase();
    }

    private void resetSharedPreferences () {
        SPHelper.sharedPreferenceInit(this);
        SPHelper.setBoolPreference(SPHelper.PREFS_NOTE_MANAGER_FIRST_LAUNCH, false);
        SPHelper.setBoolPreference(SPHelper.PREFS_NOTE_MANAGER_DO_BACKUP, false);
        SPHelper.setStringPreference(SPHelper.PREFS_NOTE_MANAGER_LAST_SORT, DBHelper.KEY_ID);
        SPHelper.setLongPreference(SPHelper.PREFS_NOTE_MANAGER_REMIND_DELAY, 3600000);
    }
}
