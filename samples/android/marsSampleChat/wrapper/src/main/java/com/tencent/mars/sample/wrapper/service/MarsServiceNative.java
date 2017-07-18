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

package com.tencent.mars.sample.wrapper.service;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import com.tencent.mars.Mars;
import com.tencent.mars.app.AppLogic;
import com.tencent.mars.sample.wrapper.remote.MarsPushMessageFilter;
import com.tencent.mars.sample.wrapper.remote.MarsService;
import com.tencent.mars.sample.wrapper.remote.MarsTaskWrapper;
import com.tencent.mars.sdt.SdtLogic;
import com.tencent.mars.stn.StnLogic;
import com.tencent.mars.xlog.Log;

/**
 * Actually Mars Service running in main app
 * Mars 的原生服务，运行在独立的 marsservice 进程，继承一个 AIDL 接口 MarsService 为主应用提供服务的传递
 * <p></p>
 * Created by zhaoyuan on 16/2/29.
 */
public class MarsServiceNative extends Service implements MarsService {

    private static final String TAG = "Mars.Sample.MarsServiceNative";

    private MarsServiceStub stub;
    //mars服务配置工厂
    private static MarsServiceProfileFactory gFactory = new MarsServiceProfileFactory() {
        @Override
        public MarsServiceProfile createMarsServiceProfile() {
            return new DebugMarsServiceProfile();
        }
    };

    //提供给外部通过工厂方法来设置自定义的 MarsServiceProfile
    public static void setProfileFactory(MarsServiceProfileFactory factory) {
        gFactory = factory;
    }

    /**
     *
     * @param taskWrapper
     * @param taskProperties
     * @return
     * @throws RemoteException
     */
    @Override
    public int send(MarsTaskWrapper taskWrapper, Bundle taskProperties) throws RemoteException {
        return stub.send(taskWrapper, taskProperties);
    }

    /**
     * 取消任务
     * @param taskID
     * @throws RemoteException
     */
    @Override
    public void cancel(int taskID) throws RemoteException {
        stub.cancel(taskID);
    }

    /**
     * 注册推送消息的过滤器（在服务连接上的时候注册）
     * @param filter
     * @throws RemoteException
     */
    @Override
    public void registerPushMessageFilter(MarsPushMessageFilter filter) throws RemoteException {
        stub.registerPushMessageFilter(filter);
    }

    /**
     * 取消注册推送消息过滤器（在服务断开连接时，取消注册）
     * @param filter
     * @throws RemoteException
     */
    @Override
    public void unregisterPushMessageFilter(MarsPushMessageFilter filter) throws RemoteException {
        stub.unregisterPushMessageFilter(filter);
    }

    /**
     * 在建立连接的时候需要设置连接的账号
     * @param uin 全局唯一标识码
     * @param userName 用户名
     */
    @Override
    public void setAccountInfo(long uin, String userName) {
        stub.setAccountInfo(uin, userName);
    }

    /**
     * STN 默认是后台，所以初始化 STN 后需要主动调用一次BaseEvent.onForeground(true)
     * @param isForeground 是否是后台
     */
    @Override
    public void setForeground(int isForeground) {
        stub.setForeground(isForeground);
    }

    @Override
    public IBinder asBinder() {
        return stub;
    }

    /**
     * 创建运行 Mars 的服务时就初始化 STN (建议在程序启动时或者使用网络之前调用)
     */
    @Override
    public void onCreate() {
        super.onCreate();

        final MarsServiceProfile profile = gFactory.createMarsServiceProfile();
        stub = new MarsServiceStub(this, profile);

        // set callback
        AppLogic.setCallBack(stub);
        StnLogic.setCallBack(stub);
        SdtLogic.setCallBack(stub);

        // Initialize the Mars PlatformComm
        Mars.init(getApplicationContext(), new Handler(Looper.getMainLooper()));

        // Initialize the Mars
        StnLogic.setLonglinkSvrAddr(profile.longLinkHost(), profile.longLinkPorts());
        StnLogic.setShortlinkSvrAddr(profile.shortLinkPort());
        StnLogic.setClientVersion(profile.productID());
        Mars.onCreate(true);

        StnLogic.makesureLongLinkConnected();

        //
        Log.d(TAG, "mars service native created");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "mars service native destroying");

        // Leave Mars
        Mars.onDestroy();  //运行 mars 的服务销毁时，同时销毁掉 mars
        // ContentDistributionNetwork.onDestroy();

        Log.d(TAG, "mars service native destroyed");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return stub;
    }
}
