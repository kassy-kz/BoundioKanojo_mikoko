package orz.kassy.androidkanojo;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import twitter4j.auth.AccessToken;

public class AppUtils {
    public static final String PREF_FILE_NAME = "pref_file";
    public static final String CONSUMER_KEY = "RKGCMJeAVzR7fCHun8tIQQ";
    public static final String CONSUMER_SECRET = "9aQfuzFcwqnRZTFgO3dQIZb7ZZymFqxa1xBZfunPm5A";
    public static final String ACCESS_TOKEN = "access_token";
    public static final String ACCESS_TOKEN_SECRET = "access_token_secret";
    public static final String AUTH_URL = "authurl";
//    private static final String TAG = "AppUTil";
    private static final String TWEET_TIMES = "tweetTimes";
    private static final String LAST_ID = "lastId";
    // ラストIDのデフォルト値（クリスマス当時のツイートを拾わないように...）
    private static final long LAST_ID_DEF = 178642519758864384L;
    
    public static AccessToken loadAccessToken(Context context) {
        SharedPreferences shPref = context.getSharedPreferences(AppUtils.PREF_FILE_NAME,Context.MODE_PRIVATE);
        String token       = shPref.getString(ACCESS_TOKEN, null);
        String tokenSecret = shPref.getString(ACCESS_TOKEN_SECRET, null);

        if(token != null && tokenSecret != null) {
            return new AccessToken(token, tokenSecret);
        } else {
            return null;
        }
    }

    public static void saveAccessToken(Context context, AccessToken accessToken) {
        
        SharedPreferences shPref = context.getSharedPreferences(AppUtils.PREF_FILE_NAME,Context.MODE_PRIVATE);
        String token       = accessToken.getToken();
        String tokenSecret = accessToken.getTokenSecret();

        Editor e = shPref.edit();
        e.putString(AppUtils.ACCESS_TOKEN, token);
        e.putString(AppUtils.ACCESS_TOKEN_SECRET, tokenSecret);
        e.commit();
        
    }
    
    /**
     * ツイートの回数を保存する
     * @param context コンテキスト
     * @param times 回数
     */
    public static void saveTweetTimes(Context context, int times) {
        SharedPreferences shPref = context.getSharedPreferences(AppUtils.PREF_FILE_NAME,Context.MODE_PRIVATE);
        Editor e = shPref.edit();
        e.putInt(TWEET_TIMES, times);
        e.commit();
    }
    
    /**
     * ツイートの回数をロードする
     * @param context コンテキスト
     * @return ツイート回数
     */
    public static int loadTweetTimes(Context context) {
        SharedPreferences shPref = context.getSharedPreferences(AppUtils.PREF_FILE_NAME,Context.MODE_PRIVATE);
        int tweetTimes = shPref.getInt(TWEET_TIMES, 0);
        return tweetTimes;
    }

    public static void saveLastId(Context context, long statusId) {
        SharedPreferences shPref = context.getSharedPreferences(AppUtils.PREF_FILE_NAME,Context.MODE_PRIVATE);
        Editor e = shPref.edit();
        e.putLong(LAST_ID, statusId);
        e.commit();
    }
    public static long loadLastId(Context context) {
        SharedPreferences shPref = context.getSharedPreferences(AppUtils.PREF_FILE_NAME,Context.MODE_PRIVATE);
        long tweetTimes = shPref.getLong(LAST_ID, LAST_ID_DEF);
        if(tweetTimes<LAST_ID_DEF)tweetTimes = LAST_ID_DEF; 
        return tweetTimes;
    }

}
