package com.media.control.helpers;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;

import com.media.control.model.ResponseData;

import java.util.ArrayList;

public class DBHelper extends SQLiteOpenHelper {
    ////////////////////////////////////////// TABLE_DATA //////////////////////////////////////////
    public static final String TABLE_AUDIO = "AUDIO";

    ////////////////////////////////////////// AUDIO_DATA //////////////////////////////////////////
    public static final String UUID = "UUID";

    public static final String URL = "URL";

    public static final String STATE = "STATE";
    public static final String DURATION = "DURATION";
    public static final String POSITION = "POSITION";
    public static final String EPOCH = "EPOCH";

    public DBHelper(Context context) {
        super(context, "audio", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_AUDIO + "(id integer primary key, " + UUID + " text, " + URL + " text, " + STATE + " text, " + DURATION + " text, " + POSITION + " text, " + EPOCH + " text)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_AUDIO);
        onCreate(db);
    }

    public ArrayList<ResponseData> getAllAudios() {
        ArrayList<ResponseData> mAudio = new ArrayList<>();
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery("Select * from " + TABLE_AUDIO, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    mAudio.add(new ResponseData(cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getString(5), cursor.getString(6)));
                    cursor.moveToNext();
                }
            }
            cursor.close();
        }
        db.close();
        return mAudio;
    }

    public void deleteAllAudio() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("delete from " + TABLE_AUDIO);
        db.close();
    }

    public long insertAudio(ResponseData mAudio) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(UUID, mAudio.getUuid());
        contentValues.put(URL, mAudio.getUrl());
        contentValues.put(STATE, mAudio.getState());
        contentValues.put(DURATION, mAudio.getDuration());
        contentValues.put(POSITION, mAudio.getPosition());
        contentValues.put(EPOCH, mAudio.getDate());

        long result;
        try {
            result = db.insert(TABLE_AUDIO, null, contentValues);
        } catch (SQLException e) {
            result = -1; // Indicates an error occurred
        }
        db.close(); // Closing database connection
        return result;
    }

    public boolean updateAudio(String uuid, String state) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(STATE, state);
        db.update(TABLE_AUDIO, contentValues, UUID + " = ? ", new String[]{uuid});
        db.close();
        return true;
    }

    public boolean audioExist(String uuid) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery("Select * from " + TABLE_AUDIO + " WHERE " + UUID + "=?", new String[]{uuid});
        if (cursor.getCount() <= 0) {
            cursor.close();
            db.close();
            return false;
        }
        cursor.close();
        db.close();
        return true;
    }
}
