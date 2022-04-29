package com.magook.dialog;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author dk
 * @date 2022年04月27日 10:55
 * 阻塞式队列Dialog，保证dialog单个且按顺序弹窗
 */
public class DialogBlockManager implements IBlockDialog, Runnable {

    private static final int SHOW_TYPE = 1;
    private static final int DISMISS_TYPE = 2;
    private static final int CANCEL_TYPE = 3;

    private final CopyOnWriteArrayList<Dialog> waitList = new CopyOnWriteArrayList<>();

    private final ReentrantLock reentrantLock = new ReentrantLock(true);
    private final Condition condition;

    private boolean isInit;

    private boolean isRunning = true;
    private boolean isDialogShow = false;

    private final Handler mHandler = new Handler(Looper.getMainLooper(), new Handler.Callback() {

        private WeakReference<Dialog> mDialog;

        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what){
                case SHOW_TYPE:
                    resetDialog();
                    if (waitList.isEmpty()) break;
                    isDialogShow = true;
                    mDialog = new WeakReference<>(waitList.remove(0));
                    try {
                        Dialog dialog = mDialog.get();
                        if (isActivityFinish(dialog.getContext())) {
                            resetDialog();
                            break;
                        }
                        //dialog show and listener status, if dismiss, call signalAll()
                        //In order to ensure functional isolation, use dynamic proxy dismiss monitoring, and call signalAll()
                        proxy(dialog);
                        dialog.show();
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        e.printStackTrace();
                        resetDialog();
                    }
                    break;
                case DISMISS_TYPE:
                    resetDialog(false);
                    break;
                case CANCEL_TYPE:
                    if (msg.obj == null){
                        resetDialog();
                    }else {
                        if (mDialog != null && Objects.equals(mDialog.get(), msg.obj)){
                            resetDialog();
                        }
                    }
                    break;
                default:
                    break;
            }
            return false;
        }

        private void resetDialog() {
            resetDialog(true);
        }
        private void resetDialog(boolean cancel) {
            isDialogShow = false;
            if (mDialog != null && mDialog.get() != null){
                if (cancel){
                    mDialog.get().cancel();
                }else {
                    mDialog.get().dismiss();
                }
            }
            if (mDialog != null){
                mDialog.clear();
                mDialog = null;
            }
        }

        private void proxy(Dialog dialog) throws NoSuchFieldException, IllegalAccessException {
            if (dialog == null) return;
            Field mDismissMessageField = Dialog.class.getDeclaredField("mDismissMessage");
            mDismissMessageField.setAccessible(true);
            Message mDismissMessage = (Message) mDismissMessageField.get(dialog);
            if (mDismissMessage == null){
                dialog.setOnDismissListener(dialogInterface -> {
                    isDialogShow = false;
                    signalAll();
                    dialog.setOnDismissListener(null);
                });
            }else {
                DialogInterface.OnDismissListener onDismissListener = (DialogInterface.OnDismissListener) mDismissMessage.obj;
                DialogInterface.OnDismissListener proxyInstance = (DialogInterface.OnDismissListener)
                        Proxy.newProxyInstance(dialog.getClass().getClassLoader(),
                                onDismissListener.getClass().getInterfaces(),
                                (proxy, method, args) -> {
                                    Object invoke = method.invoke(onDismissListener, args);
                                    System.out.println("动态代理设置的监听");
                                    isDialogShow = false;
                                    signalAll();
                                    dialog.setOnDismissListener(null);
                                    return invoke;
                                }
                        );
                dialog.setOnDismissListener(proxyInstance);
            }
        }

        private boolean isActivityFinish(Context context) {
            Activity activity = scanForActivity(context);
            return activity == null || activity.isDestroyed() || activity.isFinishing();
        }
    });

    private DialogBlockManager() {
        condition = reentrantLock.newCondition();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(this);
    }

    public static IBlockDialog getInstance() {
        return Holder.DIALOG_BLOCK_MANAGER_HOLDER;
    }

    private void signalAll(){
        try {
            reentrantLock.lock();
            condition.signalAll();
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            reentrantLock.unlock();
        }
    }

    private Activity scanForActivity(Context context) {
        if (context == null) {
            return null;
        } else if (context instanceof Activity) {
            return (Activity) context;
        } else if (context instanceof ContextWrapper) {
            return scanForActivity(((ContextWrapper) context).getBaseContext());
        } else {
            return null;
        }
    }

    @Override
    public void init(Application application){
        if (isInit) return;
        isInit = true;
        application.registerActivityLifecycleCallbacks(lifecycleCallbacks);
    }

    @Override
    public void run() {
        reentrantLock.lock();
        try {
            while (isRunning){
                condition.await();
                if (!waitList.isEmpty()) {
                    mHandler.obtainMessage(SHOW_TYPE).sendToTarget();
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            isRunning = false;
            Thread.currentThread().interrupt();
        } finally {
            reentrantLock.unlock();
        }
    }

    @Override
    public void showDialog(Dialog dialog) {
        if (!waitList.contains(dialog)){
            waitList.add(dialog);
        }
        if (!isDialogShow && waitList.size() == 1){
            signalAll();
        }
    }

    @Override
    public void dismissDialog() {
        mHandler.obtainMessage(DISMISS_TYPE).sendToTarget();
    }

    @Override
    public void cancelDialog() {
        mHandler.obtainMessage(CANCEL_TYPE).sendToTarget();
    }

    @Override
    public void removeQueue() {
        waitList.clear();
        mHandler.obtainMessage(CANCEL_TYPE).sendToTarget();
    }

    @Override
    public void removeQueue(Dialog dialog) {
        waitList.remove(dialog);
        mHandler.obtainMessage(CANCEL_TYPE, dialog).sendToTarget();
    }

    private static class Holder {
        public static final DialogBlockManager DIALOG_BLOCK_MANAGER_HOLDER = new DialogBlockManager();
    }

    private final Application.ActivityLifecycleCallbacks lifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {

        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {

        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {

        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {

        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {

        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
            for (Dialog dialog : waitList) {
                if (dialog == null) continue;
                Activity dialogContext = scanForActivity(dialog.getContext());
                if (dialogContext == null ||
                        activity.getClass().getCanonicalName().equals(dialogContext.getClass().getCanonicalName())) {
                    removeQueue(dialog);
                }
            }
        }
    };
}
