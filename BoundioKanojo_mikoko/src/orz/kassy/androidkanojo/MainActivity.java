package orz.kassy.androidkanojo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.http.client.*;

import orz.kassy.androidkanojo.R;
import twitter4j.AsyncTwitter;
import twitter4j.AsyncTwitterFactory;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterAdapter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.TwitterListener;
import twitter4j.TwitterMethod;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

/**
 * メインアクティビティクラス
 * Twitter認証
 * Timetickレシーバ設定
 * mention取得→サーバー処理
 * @author kashimoto
 */
public class MainActivity extends Activity implements OnClickListener {
	private static final int TWITTER_AUTHORIZE = 0;
    private static final String TAG = "MikokoMain";
    //private static final String SERVER_ADDRESS  = "http://192.168.0.3:8888/telreq";
    private static final String SERVER_ADDRESS  = "http://boundio-kanojo.appspot.com/telreq";
    private static MainActivity mSelf;
	private Twitter mTwitter = null;
	private RequestToken mToken = null;
    private AsyncTwitter mAsyncTwitter;
	private AccessToken mAccessToken = null;
	private String mAuthorizeUrl = "";
	private ProgressDialog mDialog = null;
	private Handler mHandler = new Handler();
    private AuthAsyncTask mTask;

    public static MainActivity getInstance() {
        return mSelf;
    }

    // 分刻みレシーバー 1分おきにmention取りに行く
    BroadcastReceiver mTimeTickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG,"time tick");
            getMikokoMentions();
            xmasTweet();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSelf = this;
        setContentView(R.layout.main);        
        Button btnsend = (Button) findViewById(R.id.btnsend);
        btnsend.setOnClickListener(this);

        // 保存したAccessToken取得
        mAccessToken = AppUtils.loadAccessToken(this);

        // 認証まだしてない場合だけ認証処理する
        if(mAccessToken == null) {
            mTask = new AuthAsyncTask(this);
            mTask.execute(0);
        // 認証が済んでいる場合はAsyncTwitterオブジェクト作る
        } else {
            AsyncTwitterFactory factory = new AsyncTwitterFactory();
            mAsyncTwitter = factory.getInstance();
            mAsyncTwitter.addListener(mAsyncTwitterListener);
            mAsyncTwitter.setOAuthConsumer(AppUtils.CONSUMER_KEY, AppUtils.CONSUMER_SECRET);
            mAsyncTwitter.setOAuthAccessToken(mAccessToken);

            // ワーカースレッドでの作業用に同期Twitterも作っておく
            TwitterFactory factory2 = new TwitterFactory();
            mTwitter = factory2.getInstance();
            mTwitter.setOAuthConsumer(AppUtils.CONSUMER_KEY, AppUtils.CONSUMER_SECRET);
            mTwitter.setOAuthAccessToken(mAccessToken);

        }

        // 分刻みで動作する（mention取得しに行く）レシーバーを設定する
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        this.getApplicationContext().registerReceiver(mTimeTickReceiver, filter);
    }
    
    
	@Override
	protected void onStop() {
		super.onStop();
		if(mTwitter != null) mTwitter.shutdown();
		//ImageCache.clear();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
	     // WebViewのOauthページの認証処理から帰ってきたとき
		if(requestCode == TWITTER_AUTHORIZE) {
			if(resultCode == 0) {
				// 認証成功
				final String pincode = data.getExtras().getString("pincode");
				
				mDialog = new ProgressDialog(this);
				mDialog.setMessage(getString(R.string.wait_timeline_message));
				mDialog.setIndeterminate(true);
				mDialog.show();

				// ワーカースレッドでTwitterオブジェクト初期化を行う
				new Thread() {
					@Override
					public void run() {
						try {
						    // 認証が成功したあとの処理
							mAccessToken = mTwitter.getOAuthAccessToken(mToken, pincode);
							
							// Preferenceに保存
							// 本番モードのみ
							AppUtils.saveAccessToken(mSelf, mAccessToken);

							// アクセス・トークンが取得できたら、リソース解放して、インスタンス再生成
							mTwitter.shutdown();
							mTwitter = null;
							
				            AsyncTwitterFactory factory = new AsyncTwitterFactory();
				            mAsyncTwitter = factory.getInstance();
				            mAsyncTwitter.addListener(mAsyncTwitterListener);
				            mAsyncTwitter.setOAuthConsumer(AppUtils.CONSUMER_KEY, AppUtils.CONSUMER_SECRET);
				            mAsyncTwitter.setOAuthAccessToken(mAccessToken);

				            // ワーカースレッドでの作業用に同期Twitterも作っておく
				            TwitterFactory factory2 = new TwitterFactory();
				            mTwitter = factory2.getInstance();
				            mTwitter.setOAuthConsumer(AppUtils.CONSUMER_KEY, AppUtils.CONSUMER_SECRET);
				            mTwitter.setOAuthAccessToken(mAccessToken);

						} catch(TwitterException e) {
							Log.d("TEST", "Exception", e);
						}
						// UIスレッドを呼び出して後処理
						mHandler.post(mRunnable_List_update);
					}
				}.start();					
			}
		}
	}

	// WEB Oauth認証処理が終わってTwitterオブジェクト初期化も終わったあとのUI後処理
    private Runnable mRunnable_List_update = new Runnable() {
        @Override
        public void run() {
            mDialog.dismiss();
        }
    };

    @Override
    public void onClick(View arg0) {
        getMikokoMentions();
    }
    
    /**
     * Mentionラインの取得（公式AsyncTwitter使用）
     */
    private void getMikokoMentions() {
        Log.i(TAG,"get mention");
        Paging paging = new Paging(1, 20);
        // メンションを非同期で取得 完了後は下のリスナーへ
        mAsyncTwitter.getMentions(paging);
    }

    /**
     *  Mention取得の後処理。実はこれワーカースレッドぽいから注意（UIいじれない）
     */
    TwitterListener mAsyncTwitterListener = new TwitterAdapter() {
        @Override
        public void gotMentions(ResponseList<Status> statuses) {
            long currentLatestId = 0;
            long previousLatestId = AppUtils.loadLastId(mSelf);
            for(Status status : statuses) {
//            Status status = statuses.get(0);
                long statusId = status.getId();
                if(statusId > currentLatestId) {
                    currentLatestId = statusId;
                }
                Log.i(TAG,"target id = "+ statusId + "  last id = " + previousLatestId);
                String targetName = status.getUser().getScreenName();
                // 前回取得のツイートよりも新しいなら返信などの対応をする
                if(previousLatestId < statusId) {
                    Log.i(TAG,"do reaction for mention");
                    // あ、どうもとか言うツイート 今は封印
                    // sendTweet(statusId, LoveString.getString(mSelf, targetName));

                    // サーバーに電話要求を投げる
                    sendPhoneCallRequest(targetName);

                    // DMを送る
                    sendDirectMessage(targetName);
                }else {
                    Log.i(TAG,"didn't send");
                }
            }
            // 今回取得したツイートで一番新しい奴のIDを保存する
            AppUtils.saveLastId(mSelf, currentLatestId);
        }
        @Override
        public void onException(TwitterException ex, TwitterMethod method) {
            Log.i(TAG,"exception");
        }
    };

    private void sendPhoneCallRequest(String twitterId) {
        Log.i(TAG,"send call request");
        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("twitter", "twitterId"));
        // パラメータをクエリに変換
        // これどうやんだろ？
        // String query = URLEncodeUtils.format(params, "UTF-8");
        HttpGet httpGet = new HttpGet(SERVER_ADDRESS + "?twitter=" + twitterId );
        Log.i(TAG,SERVER_ADDRESS + "?twitter=" + twitterId);        
        HttpClient httpClient = new DefaultHttpClient();
        try {
            HttpResponse res = httpClient.execute(httpGet);
            Log.i("res",EntityUtils.toString( res.getEntity(), "UTF-8" ));
            res.getEntity().toString();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    
    private void sendDirectMessage(final String targetName) {
        new Thread() {
            @Override
            public void run() {
                try {
//                    mSelf.wait(20000);
                    mTwitter.sendDirectMessage(targetName, "yfrog.com/h4ll7sp " + new Date().toString());
                } catch (TwitterException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }
    
    /**
     *  クリスマスへのカウントダウンツイートを行う
     *  @deprecated
     */
    private void xmasTweet() {
        Date now = new Date();
        if(now.getHours() != 0 || now.getMinutes() != 0)return;

        Calendar rightNow = Calendar.getInstance();
        GregorianCalendar xDay = new GregorianCalendar(2011,11,25);
        int restDay = (xDay.get(Calendar.DAY_OF_YEAR) - rightNow.get(Calendar.DAY_OF_YEAR));
        if(restDay == 0) {
//            sendTweet(0,SPECIAL_MESSAGE);
//            changeIcon(R.drawable.special_icon);
        } else if(restDay > 0) {
            sendTweet(0," クリスマスまであと "+restDay +"日ですね... ///");
        }
    }

    /**
     * ツイートする
     * @param inReplyTo 返信先ツイートID
     * @param str ツイート文言
     */
    private void sendTweet(long inReplyTo, String str) {
        Log.i(TAG,"send tweet = " + str + " to "+ inReplyTo);
        StatusUpdate statusUpdate = new StatusUpdate(str);
        statusUpdate.setInReplyToStatusId(inReplyTo);
        
        AsyncTwitterFactory factory = new AsyncTwitterFactory();
        AsyncTwitter asyncTwitter = factory.getInstance();
        asyncTwitter.addListener(mAsyncTwitterListener);
        asyncTwitter.setOAuthConsumer(AppUtils.CONSUMER_KEY, AppUtils.CONSUMER_SECRET);
        asyncTwitter.setOAuthAccessToken(mAccessToken);
        asyncTwitter.updateStatus(statusUpdate);
    }

    /**
     * 認証処理の非同期タスク　（WebViewでOAuthのページに飛ぶタスク）
     */
    public class AuthAsyncTask extends AsyncTask<Integer, Void, Integer>{
        private Activity mActivity;
        private static final int RESULT_OK = 0;
        private static final int RESULT_NG = -1;
        
        public AuthAsyncTask(Activity activity) {
            mActivity = activity;
        }

        // 認証処理の前処理 これはUIスレッドでの処理
        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            // ダイアログを表示
            mDialog = new ProgressDialog(mActivity);
            mDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mDialog.setMessage("認証処理をしています...");
            mDialog.setCancelable(true);
            mDialog.show();
        }
        
        // 認証処理（ワーカースレッドでの処理）
        @Override
        protected Integer doInBackground(Integer... arg0) {

            // 保存したAccessToken取得
            mAccessToken = AppUtils.loadAccessToken(mActivity);
            
            // 初回の認証処理
            mTwitter = new TwitterFactory().getInstance();
            mTwitter.setOAuthConsumer(AppUtils.CONSUMER_KEY, AppUtils.CONSUMER_SECRET);
            AsyncTwitterFactory factory = new AsyncTwitterFactory();
            mAsyncTwitter = factory.getInstance();
            mAsyncTwitter.addListener(mAsyncTwitterListener);
            mAsyncTwitter.setOAuthConsumer(AppUtils.CONSUMER_KEY, AppUtils.CONSUMER_SECRET);
            mAsyncTwitter.setOAuthAccessToken(mAccessToken);

            // ワーカースレッドでの作業用に同期Twitterも作っておく
            TwitterFactory factory2 = new TwitterFactory();
            mTwitter = factory2.getInstance();
            mTwitter.setOAuthConsumer(AppUtils.CONSUMER_KEY, AppUtils.CONSUMER_SECRET);
            mTwitter.setOAuthAccessToken(mAccessToken);

            try {
                mToken = mTwitter.getOAuthRequestToken();
                mAuthorizeUrl = mToken.getAuthorizationURL();
                return RESULT_OK;
            } catch (TwitterException e) {
                e.printStackTrace();
                return RESULT_NG;
            }
        }

        // 認証の後処理 （UIスレッドでの処理 WebViewのOAuthページに飛ぶ）
        @Override
        protected void onPostExecute (Integer result) {
            super.onPostExecute(result);
            if(result == RESULT_OK) {
                if(mDialog != null) {
                    mDialog.dismiss();
                }
                // WebViewを持つアクティビティを呼び出す
                Intent intent = new Intent(mActivity, TwitterAuthorizeActivity.class);
                intent.putExtra(AppUtils.AUTH_URL, mAuthorizeUrl);
                mActivity.startActivityForResult(intent, TWITTER_AUTHORIZE);
            } else if(result == RESULT_NG) {
                if(mDialog != null) {
                    mDialog.dismiss();
                }
                Toast.makeText(mActivity, R.string.twitter_auth_error, Toast.LENGTH_SHORT).show();
            }
        }
    }
}