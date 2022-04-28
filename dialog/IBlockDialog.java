package cn.com.bookan.popupdemo.dialog;

import android.app.Dialog;
import android.content.DialogInterface;

public interface IBlockDialog {

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
}
