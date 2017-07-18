/*
* Tencent is pleased to support the open source community by making Mars available.
* Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
*
* Licensed under the MIT License (the "License"); you may not use this file except in 
* compliance with the License. You may obtain a copy of the License at
* http://opensource.org/licenses/MIT
*
* Unless required by applicable law or agreed to in writing, software distributed under the License is
* distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
* either express or implied. See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.tencent.mars.sample.core;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.support.v7.app.NotificationCompat;

import com.google.protobuf.nano.InvalidProtocolBufferNanoException;
import com.tencent.mars.sample.R;
import com.tencent.mars.sample.SampleApplicaton;
import com.tencent.mars.sample.chat.proto.Messagepush;
import com.tencent.mars.sample.utils.Constants;
import com.tencent.mars.sample.wrapper.remote.PushMessage;
import com.tencent.mars.xlog.Log;

import static android.content.Context.NOTIFICATION_SERVICE;

/**
 * Created by caoshaokun on 16/12/20.
 */
public class MessageHandler extends BusinessHandler {

    public static String TAG = MessageHandler.class.getSimpleName();

    @Override
    public boolean handleRecvMessage(PushMessage pushMessage) {

        switch (pushMessage.cmdId) {
            case Constants.PUSHCMD: {
                try {
                    Messagepush.MessagePush message = Messagepush.MessagePush.parseFrom(pushMessage.buffer);
                    Intent intent = new Intent();
                    intent.setAction(Constants.PUSHACTION);
                    intent.putExtra("msgfrom", message.from);
                    intent.putExtra("msgcontent", message.content);
                    intent.putExtra("msgtopic", message.topic);
//                    showNotification(intent, "来自" + message.from + "的消息", message.content);
                    SampleApplicaton.getContext().sendBroadcast(intent);   //接收到服务端发送的数据通过广播发送给 ChatDataCore的接收器
                } catch (InvalidProtocolBufferNanoException e) {
                    Log.e(TAG, "%s", e.toString());
                }
            }
            return true;
            default:
                break;
        }

        return false;
    }

    //添加消息的通知功能（有问题，暂时不调用）
    private void showNotification(Intent intent, String title, String content){
        //设置点击通知栏启动另外一个广播打开指定的页面
        Intent broadcastIntent = new Intent(SampleApplicaton.getContext(), NotificationReceiver.class);
        broadcastIntent.putExtras(intent);  //设置需要传递的消息数据
        PendingIntent pendingIntent = PendingIntent.getBroadcast(SampleApplicaton.getContext(), 0, broadcastIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        //启动通知栏
        NotificationManager manager = (NotificationManager) SampleApplicaton.getContext().getSystemService(NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(SampleApplicaton.getContext());
        Notification notification = builder
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pendingIntent)  //添加点击通知的动作意图
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(
                        SampleApplicaton.getContext().getResources(), R.mipmap.ic_launcher))
                .build();
        manager.notify(1, notification);
    }
}
