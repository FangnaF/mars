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

import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;

import com.tencent.mars.BaseEvent;
import com.tencent.mars.app.AppLogic;
import com.tencent.mars.sample.utils.print.BaseConstants;
import com.tencent.mars.sample.wrapper.remote.MarsPushMessageFilter;
import com.tencent.mars.sample.wrapper.remote.MarsService;
import com.tencent.mars.sample.wrapper.remote.MarsTaskProperty;
import com.tencent.mars.sample.wrapper.remote.MarsTaskWrapper;
import com.tencent.mars.sdt.SdtLogic;
import com.tencent.mars.stn.StnLogic;
import com.tencent.mars.xlog.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Mars Task Wrapper implements.
 * Mars Task 的包装类的实现(包含需要处理的app逻辑、stn逻辑)
 */
public class MarsServiceStub extends MarsService.Stub implements StnLogic.ICallBack, SdtLogic.ICallBack, AppLogic.ICallBack {

    private static final String TAG = "Mars.Sample.MarsServiceStub";

    private final MarsServiceProfile profile;

    private AppLogic.AccountInfo accountInfo = new AppLogic.AccountInfo();

    //设备名称 = 制造商 + 型号
    public static final String DEVICE_NAME = android.os.Build.MANUFACTURER + "-" + android.os.Build.MODEL;
    //设备类型 = "android" + Android版本号
    public static String DEVICE_TYPE = "android-" + android.os.Build.VERSION.SDK_INT;
    private AppLogic.DeviceInfo info = new AppLogic.DeviceInfo(DEVICE_NAME, DEVICE_TYPE);

    private Context context;
    //存储发送消息过滤器的队列
    private ConcurrentLinkedQueue<MarsPushMessageFilter> filters = new ConcurrentLinkedQueue<>();

    private int clientVersion = 200;

    public MarsServiceStub(Context context, MarsServiceProfile profile) {
        this.context = context;
        this.profile = profile;
    }

    private static final int FIXED_HEADER_SKIP = 4 + 2 + 2 + 4 + 4;

    private static Map<Integer, MarsTaskWrapper> TASK_ID_TO_WRAPPER = new ConcurrentHashMap<>();

    @Override
    public int send(final MarsTaskWrapper taskWrapper, Bundle taskProperties) throws RemoteException {
        final StnLogic.Task _task = new StnLogic.Task(StnLogic.Task.EShort, 0, "", null);  //初始化为

        // Set host & cgi path
        final String host = taskProperties.getString(MarsTaskProperty.OPTIONS_HOST);
        final String cgiPath = taskProperties.getString(MarsTaskProperty.OPTIONS_CGI_PATH);
        _task.shortLinkHostList = new ArrayList<>();
        _task.shortLinkHostList.add(host);
        _task.cgi = cgiPath;

        final boolean shortSupport = taskProperties.getBoolean(MarsTaskProperty.OPTIONS_CHANNEL_SHORT_SUPPORT, true);
        final boolean longSupport = taskProperties.getBoolean(MarsTaskProperty.OPTIONS_CHANNEL_LONG_SUPPORT, false);
        if (shortSupport && longSupport) {
            _task.channelSelect = StnLogic.Task.EBoth;

        } else if (shortSupport) {
            _task.channelSelect = StnLogic.Task.EShort;

        } else if (longSupport) {
            _task.channelSelect = StnLogic.Task.ELong;

        } else {
            Log.e(TAG, "invalid channel strategy");
            throw new RemoteException("Invalid Channel Strategy");
        }

        // Set cmdID if necessary
        int cmdID = taskProperties.getInt(MarsTaskProperty.OPTIONS_CMD_ID, -1);
        if (cmdID != -1) {
            _task.cmdID = cmdID;
        }

        TASK_ID_TO_WRAPPER.put(_task.taskID, taskWrapper);

        // Send 发送任务
        Log.i(TAG, "now start task with id %d", _task.taskID);
        StnLogic.startTask(_task);
        if (StnLogic.hasTask(_task.taskID)) {
            Log.i(TAG, "stn task started with id %d", _task.taskID);

        } else {
            Log.e(TAG, "stn task start failed with id %d", _task.taskID);
        }

        return _task.taskID;
    }

    @Override
    public void cancel(int taskID) throws RemoteException {
        Log.d(TAG, "cancel wrapper with taskID=%d using stn stop", taskID);
        StnLogic.stopTask(taskID);
        TASK_ID_TO_WRAPPER.remove(taskID); // TODO: check return
    }


    @Override
    public void registerPushMessageFilter(MarsPushMessageFilter filter) throws RemoteException {
        filters.remove(filter);  //避免重复添加到队列，先 remove，再 add
        filters.add(filter);
    }

    @Override
    public void unregisterPushMessageFilter(MarsPushMessageFilter filter) throws RemoteException {
        filters.remove(filter);
    }

    @Override
    public void setAccountInfo(long uin, String userName) {
        accountInfo.uin = uin;
        accountInfo.userName = userName;
    }

    /**
     * STN 默认是后台，所以初始化 STN 后需要主动调用一次BaseEvent.onForeground(true)
     * @param isForeground
     */
    @Override
    public void setForeground(int isForeground) {
        BaseEvent.onForeground(isForeground == 1);
    }

    /**
     * [stn 回调方法]
     * 允许拦截所有需要认证操作的任务，通常使用这个交换加密索引，session 等
     * @return
     */
    @Override
    public boolean makesureAuthed() {
        //
        // Allow you to block all tasks which need to be sent before certain 'AUTHENTICATED' actions
        // Usually we use this to exchange encryption keys, sessions, etc.
        //
        return true;
    }

    /**
     * [stn 回调方法]
     * 域名解析，可以实现传统DNS解析，或者自己实现的域名/IP映射
     * @param host
     * @return
     */
    @Override
    public String[] onNewDns(String host) {
        // No default new dns support
        return new String[]{"192.168.24.193"};
    }

    /**
     * [stn 回调方法]
     * 收到SVR PUSH下来的消息
     * @param cmdid 指令 id
     * @param data 消息的数据
     */
    @Override
    public void onPush(int cmdid, byte[] data) {
        for (MarsPushMessageFilter filter : filters) {
            try {
                if (filter.onRecv(cmdid, data)) {
                    break;
                } //TODO 接收失败呢？？？

            } catch (RemoteException e) {
                //
            }
        }
    }

    /**
     * [stn 回调方法]
     * 流量统计
     * @param send
     * @param recv
     */
    @Override
    public void trafficData(int send, int recv) {
        onPush(BaseConstants.FLOW_CMDID, String.format("%d,%d", send, recv).getBytes(Charset.forName("UTF-8")));
    }

    /**
     * [stn 回调方法]
     * 连接状态通知
     * @param status    综合状态，即长连+短连的状态
     * @param longlinkstatus    仅长连的状态
     */
    @Override
    public void reportConnectInfo(int status, int longlinkstatus) {

    }

    /**
     * [stn 回调方法]
     * SDK要求上层生成长链接数据校验包,在长链接连接上之后使用,用于验证SVR身份
     * @param identifyReqBuf    校验包数据内容
     * @param hashCodeBuffer    校验包的HASH
     * @param reqRespCmdID      数据校验的CMD ID
     * @return  ECHECK_NOW(需要校验), ECHECK_NEVER(不校验), ECHECK_NEXT(下一次再询问)
     */
    @Override
    public int getLongLinkIdentifyCheckBuffer(ByteArrayOutputStream identifyReqBuf, ByteArrayOutputStream hashCodeBuffer, int[] reqRespCmdID) {
        // Send identify request buf to server
        // identifyReqBuf.write();

        return StnLogic.ECHECK_NEVER;
    }

    /**
     * [stn 回调方法]
     * SDK要求上层解连接校验回包.
     * @param buffer            SVR回复的连接校验包
     * @param hashCodeBuffer    CLIENT请求的连接校验包的HASH值
     * @return
     */
    @Override
    public boolean onLongLinkIdentifyResp(byte[] buffer, byte[] hashCodeBuffer) {
        return false;
    }

    /**
     * [stn 回调方法]
     * 请求做sync
     */
    @Override
    public void requestDoSync() {

    }

    @Override
    public String[] requestNetCheckShortLinkHosts() {
        return new String[0];
    }

    /**
     * [stn 回调方法]
     * 是否登录
     * @return true 登录 false 未登录
     */
    @Override
    public boolean isLogoned() {
        return false;
    }

    /**
     * [stn 回调方法]
     * 任务结束回调
     * @param taskID            任务标识
     * @param userContext
     * @param errType           错误类型
     * @param errCode           错误码
     * @return
     */
    @Override
    public int onTaskEnd(int taskID, Object userContext, int errType, int errCode) {
        final MarsTaskWrapper wrapper = TASK_ID_TO_WRAPPER.remove(taskID);
        if (wrapper == null) {
            Log.w(TAG, "stn task onTaskEnd callback may fail, null wrapper, taskID=%d", taskID);
            return 0; // TODO: ???
        }

        try {
            wrapper.onTaskEnd(errType, errCode);

        } catch (RemoteException e) {
            e.printStackTrace();

        }

        return 0;
    }

    /**
     * [stn 回调方法]
     * SDK要求上层对TASK组包
     * @param taskID    任务标识
     * @param userContext
     * @param reqBuffer 组包的BUFFER
     * @param errCode   组包的错误码
     * @return
     */
    @Override
    public boolean req2Buf(int taskID, Object userContext, ByteArrayOutputStream reqBuffer, int[] errCode, int channelSelect) {
        final MarsTaskWrapper wrapper = TASK_ID_TO_WRAPPER.get(taskID);
        if (wrapper == null) {
            Log.e(TAG, "invalid req2Buf for task, taskID=%d", taskID);
            return false;
        }

        try {
            reqBuffer.write(wrapper.req2buf());
            return true;

        } catch (IOException | RemoteException e) {
            e.printStackTrace();
            Log.e(TAG, "task wrapper req2buf failed for short, check your encode process");
        }

        return false;
    }

    /**
     * [stn 回调方法]
     * SDK要求上层对TASK解包
     * @param taskID        任务标识
     * @param userContext
     * @param respBuffer    要解包的BUFFER
     * @param errCode       解包的错误码
     * @return  int
     */
    @Override
    public int buf2Resp(int taskID, Object userContext, byte[] respBuffer, int[] errCode, int channelSelect) {
        final MarsTaskWrapper wrapper = TASK_ID_TO_WRAPPER.get(taskID);
        if (wrapper == null) {
            Log.e(TAG, "buf2Resp: wrapper not found for stn task, taskID=%", taskID);
            return StnLogic.RESP_FAIL_HANDLE_TASK_END;
        }

        try {
            return wrapper.buf2resp(respBuffer);

        } catch (RemoteException e) {
            Log.e(TAG, "remote wrapper disconnected, clean this context, taskID=%d", taskID);
            TASK_ID_TO_WRAPPER.remove(taskID);
        }
        return StnLogic.RESP_FAIL_HANDLE_TASK_END;
    }


    @Override
    public void reportTaskProfile(String reportString) {
        onPush(BaseConstants.CGIHISTORY_CMDID, reportString.getBytes(Charset.forName("UTF-8")));
    }

    /**
     * [sdt 回调方法]
     * 信令探测回调接口，启动信令探测
     * @param reportString
     */
    @Override
    public void reportSignalDetectResults(String reportString) {
        onPush(BaseConstants.SDTRESULT_CMDID, reportString.getBytes(Charset.forName("UTF-8")));
    }

    /**
     * [AppLogic 的回调方法]
     * STN 会将配置文件进行存储，如连网IPPort策略、心跳策略等，此类信息将会被存储在客户端上层指定的目录下
     * @return
     */
    @Override
    public String getAppFilePath() {
        if (null == context) {
            return null;
        }

        try {
            File file = context.getFilesDir();
            if (!file.exists()) {
                file.createNewFile();
            }
            return file.toString();
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }

        return null;
    }

    /**
     * [AppLogic 的回调方法]
     * STN 会根据客户端的登陆状态进行网络连接策略的动态调整，当用户非登陆态时，网络会将连接的频率降低
     * 所以需要获取用户的帐号信息，判断用户是否已登录
     * @return
     */
    @Override
    public AppLogic.AccountInfo getAccountInfo() {
        return accountInfo;
    }

    /**
     * [AppLogic 的回调方法]
     * 客户端版本号能够帮助 STN 清晰区分存储的网络策略配置文件。
     * @return 客户端版本号
     */
    @Override
    public int getClientVersion() {
        return clientVersion;
    }

    /**
     * [AppLogic 的回调方法]
     * 客户端通过获取设备类型，加入到不同的上报统计回调中，供客户端进行数据分析
     * @return
     */
    @Override
    public AppLogic.DeviceInfo getDeviceType() {
        return info;
    }
}