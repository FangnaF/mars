package com.tencent.mars.sample.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.tencent.mars.sample.ConversationActivity;
import com.tencent.mars.sample.utils.Constants;
import com.tencent.mars.sample.utils.SystemUtils;
import com.tencent.mars.xlog.Log;

/**
 * Created by cfanr on 2017/7/7.
 * 参考：http://www.jianshu.com/p/224e2479da18
 */

public class NotificationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        //判断app进程是否存活
        if(SystemUtils.isAppAlive(context, "com.tencent.mars.sample")){
            //如果存活的话，就直接启动聊天界面 ChatActivity，但要考虑一种情况，就是app的进程虽然仍然在
            //但Task栈已经空了，比如用户点击Back键退出应用，但进程还没有被系统回收，就要先启动 ConversationActivity
            //避免按返回键时不会返回上个页面
            Log.i("NotificationReceiver", "the app process is alive");
            Intent mainIntent = new Intent(context, ConversationActivity.class);
            //将 ConversationActivity 的launchMode设置成SingleTask, 或者在下面flag中加上Intent.FLAG_CLEAR_TOP,
            //如果Task栈中有MainActivity的实例，就会把它移到栈顶，把在它之上的Activity都清理出栈，
            //如果Task栈不存在MainActivity实例，则在栈顶创建
            mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

//            Intent chatIntent = new Intent(context, ChatActivity.class);
            //注意，app 进程还存活时，不需要发送通知消息数据（ChatDataCore 的接收器会接收）
//            chatIntent.putExtra(Constants.KEY_CHAT_MSG, intent);
//            Intent[] intents = {mainIntent, chatIntent};

            context.startActivity(mainIntent);
        }else {
            //如果app进程已经被杀死，先重新启动app，将通知的数据传入Intent中，进入主页后，判断是否有数据，再传递给指定页面
            Log.i("NotificationReceiver", "the app process is dead");
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage("com.tencent.mars.sample");
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            launchIntent.putExtra(Constants.KEY_CHAT_MSG, intent);
            context.startActivity(launchIntent);
        }
    }
}
