package com.iamnotafondrik.notesmanager;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.google.android.gms.actions.NoteIntents;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class FullNoteActivity extends AppCompatActivity implements View.OnClickListener, GroupPicker.GroupPickerListener,
        DeleteNoteDialog.DeleteDialogListener {

    EditText description;
    ImageView groups;
    Switch switcher, remindSwitcher;

    String noteId, oldDescription, pinned, oldPinned, remind, oldRemind;
    // REQUEST 0 - new note, 1 - edit note
    int groupId, oldGroupId, request;

    SQLiteDatabase database;
    ContentValues contentValues;

    long remindTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_note);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbarFullNote);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onBackPressed();
                }
            });
        }

        description = (EditText) findViewById(R.id.fullNoteDescriptionEditText);
        description.setMovementMethod(LinkMovementMethod.getInstance());
        groups = (ImageView) findViewById(R.id.fullNoteIcon);
        switcher = (Switch) findViewById(R.id.switcher);
        remindSwitcher = (Switch) findViewById(R.id.remind_switcher) ;
        groups.setOnClickListener(this);
        switcher.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    pinned = "YES";
                } else {
                    pinned = "NO";
                }

                if (!description.getText().toString().equals("")) {
                    saveNote();
                    updateWidget();
                }
            }
        });

        remindSwitcher.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    remind = "YES";
                    if (!description.getText().toString().equals("")) {
                        remindTime = createRemind();
                        remindToast();
                        saveNote();
                    }
                } else {
                    remind = "NO";
                    cancelRemind();
                    if (!description.getText().toString().equals("")) {
                        saveNote();
                    }
                }
            }
        });

        DBHelper dbHelper = new DBHelper(this);
        database = dbHelper.getReadableDatabase();
        contentValues = new ContentValues();

        request = getIntent().getIntExtra("request", 0);

        prepareNote();

        //GET INTENT
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) || NoteIntents.ACTION_CREATE_NOTE.equals(action)) {
            if (type != null) {
                if ("text/plain".equals(type)) {
                    String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                    if (sharedText != null) {
                        description.setText(sharedText);
                    }
                }
            }
        }

        //Log.d ("Note_", "NOTE INFO: " + noteId + ", " + description.getText().toString() + ", " + groupId);
    }


    @Override
    protected void onPause() {
        saveNote();
        updateWidget();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        overridePendingTransition(R.anim.slide_in_left, R.anim.alpha_out);
        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_full_note, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_share_note) {
            if (!description.getText().toString().equals("")) {
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, String.format("%s", description.getText().toString()));
                sendIntent.setType("text/plain");
                startActivity(sendIntent);
            } else {
                Toast.makeText(this, R.string.cant_share_empty_note, Toast.LENGTH_SHORT).show();
            }
        }
        if (id == R.id.action_delete_note) {
            DeleteNoteDialog deleteNoteDialog = new DeleteNoteDialog(this);
            deleteNoteDialog.createDeleteDialog();
        }
        return super.onOptionsItemSelected(item);
    }

    private void prepareNote() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        if (request == 0) {
            contentValues.put(DBHelper.KEY_DESCRIPTION, "");
            contentValues.put(DBHelper.KEY_DATE, "");
            contentValues.put(DBHelper.KEY_GROUP, 0);
            contentValues.put(DBHelper.KEY_PINNED, "NO");
            contentValues.put(DBHelper.KEY_REMIND, "NO");
            database.insert(DBHelper.TABLE_NOTES, null, contentValues);

            Cursor cursor = database.query(DBHelper.TABLE_NOTES, null, null, null, null, null, null);
            cursor.moveToLast();
            noteId = cursor.getString(cursor.getColumnIndex(DBHelper.KEY_ID));
            cursor.close();

            setGroup(0);
            pinned = "NO";
            oldPinned = "NO";
            remind = "NO";
            oldRemind = "NO";
            switcher.setChecked(false);
            remindSwitcher.setChecked(false);
            imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, InputMethodManager.HIDE_NOT_ALWAYS);
        }

        if (request == 1) {
            noteId = getIntent().getStringExtra("noteId");
            Cursor cursor = database.query(DBHelper.TABLE_NOTES, null, "_id = ?", new String[]{noteId}, null, null, null);
            cursor.moveToFirst();

            description.setText(cursor.getString(cursor.getColumnIndex(DBHelper.KEY_DESCRIPTION)));
            groupId = cursor.getInt(cursor.getColumnIndex(DBHelper.KEY_GROUP));
            pinned = cursor.getString(cursor.getColumnIndex(DBHelper.KEY_PINNED));
            remind = cursor.getString(cursor.getColumnIndex(DBHelper.KEY_REMIND));
            cursor.close();
            oldDescription = description.getText().toString();
            oldGroupId = groupId;
            oldPinned = pinned;
            oldRemind = remind;
            setGroup(groupId);

            if (pinned.equals("YES")) {
                switcher.setChecked(true);
            } else {
                switcher.setChecked(false);
            }

            if (remind.equals("YES")) {
                remindSwitcher.setChecked(true);
            } else {
                remindSwitcher.setChecked(false);
            }

            imm.hideSoftInputFromWindow(description.getWindowToken(), 0);

        }
    }

    private void saveNote() {
        if (!description.getText().toString().equals(oldDescription) || groupId != oldGroupId || !pinned.equals(oldPinned)
                || !remind.equals(oldRemind)) {
            if (description.getText().toString().equals("")) {
                database.delete(DBHelper.TABLE_NOTES, "_id = " + noteId, null);
            } else {

                contentValues.put(DBHelper.KEY_DESCRIPTION, description.getText().toString());

                contentValues.put(DBHelper.KEY_DATE, String.valueOf(System.currentTimeMillis()));

                contentValues.put(DBHelper.KEY_GROUP, groupId);
                contentValues.put(DBHelper.KEY_PINNED, pinned);
                contentValues.put(DBHelper.KEY_REMIND, remind);
                database.update(DBHelper.TABLE_NOTES, contentValues, "_id = ?", new String[]{noteId});
            }
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fullNoteIcon:
                GroupPicker groupPicker = new GroupPicker(this);
                groupPicker.show();
                break;
        }
    }

    @Override
    public void setGroup(int id) {
        groupId = id;
        switch (id) {
            case 0:
                groups.setImageResource(R.drawable.ic_group_regular);
                break;
            case 1:
                groups.setImageResource(R.drawable.ic_group_home);
                break;
            case 2:
                groups.setImageResource(R.drawable.ic_group_work);
                break;
            case 3:
                groups.setImageResource(R.drawable.ic_group_important);
                break;
        }
    }

    @Override
    public void deleteNote() {
        database.delete(DBHelper.TABLE_NOTES, "_id = " + noteId, null);
        updateWidget();
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.alpha_out);
    }

    void updateWidget() {
        if (!description.getText().toString().equals("")) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getApplicationContext());
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(this, WidgetNotes.class));
            if (appWidgetIds.length > 0) {
                new WidgetNotes().onUpdate(this, appWidgetManager, appWidgetIds);
            }
        }
    }

    private long createRemind() {
        Intent alarmIntent = new Intent(this, NotificationReceiver.class);
        alarmIntent.putExtra("noteId", noteId);
        alarmIntent.putExtra("title", getString(R.string.reminder));
        alarmIntent.putExtra("message", description.getText().toString());
        AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        long delay = SPHelper.getLongPreference(SPHelper.PREFS_NOTE_MANAGER_REMIND_DELAY);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delay, pendingIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delay, pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delay, pendingIntent);
        }
        return System.currentTimeMillis() + delay;
    }

    private void cancelRemind() {
        Intent intent = new Intent(this, NotificationReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);
    }

    private void remindToast() {
        if (remind.equals("YES")) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(" kk:mm", Locale.getDefault());
            String dateFormated = simpleDateFormat.format(new Date(remindTime));
            Toast.makeText(this, getString(R.string.remind_toast) + dateFormated,
                    Toast.LENGTH_SHORT).show();
        }
    }
}
