# BlockDialog

## 目录

dialog阻塞管理

ReentrantLock实现阻塞队列

生命周期处理，无需关系内存泄漏

动态代理，兼容android所有版本，避免创建Dialog代码侵入

### step1 初始化&#x20;

```java
DialogBlockManager.getInstance().init(Application);
```

### step2 调用

```java
    void init(Application application);

    /**
     * add dialog to block queue. if queue is empty, dialog will be call show();
     * if you want to set a listener to be invoked when the dialog is dismissed,
     * call {@link Dialog#setOnDismissListener(DialogInterface.OnDismissListener)} before the method.
     * @param dialog Dialog
     */
    void showDialog(Dialog dialog);

    void dismissDialog();

    void cancelDialog();

    void removeQueue();

    void removeQueue(Dialog dialog);
```

> 如果需要监听dialog关闭事件，请在showDialog()方法之前调用dialog.OnDismissListener(DialogInterface.OnDismissListener)&#x20;
