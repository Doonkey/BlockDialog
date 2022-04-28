# BlockDialog
阻塞式dialog管理
##step1
初始化
  DialogBlockManager.getInstance()
##step2
使用方式
注意showDialog()方法使用，如果队列为空，对话框将被调用 show();如果监听dialog关闭事件，请在此方法之前调用 {@link Dialogset#OnDismissListener(DialogInterface.OnDismissListener)}。 
 ![image](https://user-images.githubusercontent.com/9332309/165727518-277b4e77-068a-48a1-a21c-5aafe7f19af2.png)
