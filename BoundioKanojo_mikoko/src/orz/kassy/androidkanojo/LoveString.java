package orz.kassy.androidkanojo;

import java.util.ArrayList;
import java.util.Random;

import android.content.Context;

public class LoveString {
    static ArrayList<String> listStr = new ArrayList<String>();

    static String[] loveStrings = {
       "@%s さん愛しています",
       "@%s さん好きです",
       "@%s さんと牛丼食べたいです",
       "@%s さん一生憑いていきます",
       "@%s 沈みましょう、共に...（沼の底へ）"
    };
    
    /**
     * もう使わない
     * @deprecated
    */
    @SuppressWarnings("unused")
    private String getLoveString(String targetName) {
        Random rnd = new Random();
        int i = rnd.nextInt(loveStrings.length);
        String loveString = String.format(loveStrings[i], targetName);
        return loveString;
    }

    
    /**
     * ラブツイートの文言取得する関数
     * @param con コンテキスト
     * @param screenName 相手の名前
     * @return 文言
     */
    public static String getString(Context con, String screenName){
        String retStr;
        String timesStr;
        Random rnd = new Random();
        int i = rnd.nextInt(loveStrings.length);
        int times = AppUtils.loadTweetTimes(con) + 1;
        AppUtils.saveTweetTimes(con, times);
        timesStr = " (" +times + "回目)";
        // kassy_kzにだけ愛の言葉
        if(screenName.equals("kassy_kz")){
            retStr = String.format(loveStrings[i], screenName)  + timesStr ;
        // 他の人にはテキトーな相槌
        } else {
            retStr = "@" + screenName + " あ、どうも..." + timesStr;            
        }
        return retStr;
    }
}
