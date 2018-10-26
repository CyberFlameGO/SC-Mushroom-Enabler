package io.github.jroy.snapchatmushroomenabler;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.chrisplus.rootmanager.RootManager;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.io.SuFile;
import com.topjohnwu.superuser.io.SuFileOutputStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private final String LINE_SEPARATOR = System.getProperty("line.separator");
    private Button launchButton;
    private Button enableButton;
    private Button disableButton;
    boolean mushroomEnabled = false;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button infoButton = findViewById(R.id.infoButton);
        launchButton = findViewById(R.id.launchButton);
        enableButton = findViewById(R.id.enableButton);
        disableButton = findViewById(R.id.disableButton);
        TextView rootState = findViewById(R.id.rootState);
        infoButton.setEnabled(false);
        launchButton.setEnabled(false);
        enableButton.setEnabled(false);
        disableButton.setEnabled(false);
        if (!RootManager.getInstance().obtainPermission()) {
            rootState.setText("Status:\nNot Rooted");
            return;
        }
        if (!isInstalled()) {
            rootState.setText("Status:\nSnapchat is not installed!");
            return;
        }
        if (!isBeta()) {
            rootState.setText("Status:\nYou must have the Snapchat beta app to enable mushroom!");
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                .setTitle("Initializing")
                .setMessage("Reading Shared Preferences...")
                .setCancelable(false)
                .create();
        dialog.show();
        List<String> prefs = readPrefs();
        dialog.setMessage("Parsing Shared Preferences...");
        infoButton.setEnabled(true);
        setMushroomEnabled(getAppFamily(prefs).equals("mushroom"));
        dialog.setMessage("Done!");
        dialog.dismiss();
        rootState.setText("Status:\nAll Good");

        infoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                List<String> prefs = readPrefs();
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Snapchat Build Info")
                        .setMessage("App Family: " + getAppFamily(prefs) + LINE_SEPARATOR + "Previous App Version: " + getPrevVer(prefs))
                        .setCancelable(false)
                        .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        }).show();
                setMushroomEnabled(getAppFamily(prefs).equals("mushroom"));
            }
        });

        launchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.snapchat.android", "com.snap.mushroom.MainActivity"));
                startActivity(intent);
                finish();
            }
        });

        enableButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //XML Parsing is overrated ;)
                List<String> prefsContents = readPrefs();
                prefsContents = replaceList(prefsContents, "<string name=\"appFamily\">snapchat</string>", "<string name=\"appFamily\">mushroom</string>");
                int curAppVer = getPrevVer(prefsContents);
                prefsContents = replaceList(prefsContents, "<int name=\"previousAppVersion\" value=\"" + curAppVer + "\" />", "<int name=\"previousAppVersion\" value=\""+(curAppVer + 1)+"\" />");
                try {
                    updatePrefs(prefsContents);
                } catch (IOException e) {
                    Toast.makeText(MainActivity.this, "Unable to save prefs file!", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                    return;
                }
                Shell.su("am force-stop com.snapchat.android").exec();
                Shell.su("pm enable com.snapchat.android/com.snap.mushroom.MushroomMainActivity",
                        "pm enable com.snapchat.android/com.snap.mushroom.MainActivity",
                        "pm disable com.snapchat.android/.LandingPageActivity").exec();
                Toast.makeText(MainActivity.this, "All Done!", Toast.LENGTH_SHORT).show();
                setMushroomEnabled(true);
            }
        });

        disableButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //XML Parsing is overrated ;)
                List<String> prefsContents = readPrefs();
                prefsContents = replaceList(prefsContents, "<string name=\"appFamily\">mushroom</string>", "<string name=\"appFamily\">snapchat</string>");
                int curAppVer = getPrevVer(prefsContents);
                prefsContents = replaceList(prefsContents, "<int name=\"previousAppVersion\" value=\"" + curAppVer + "\" />", "<int name=\"previousAppVersion\" value=\""+(curAppVer - 1)+"\" />");
                try {
                    updatePrefs(prefsContents);
                } catch (IOException e) {
                    Toast.makeText(MainActivity.this, "Unable to save prefs file!", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                    return;
                }
                Shell.su("am force-stop com.snapchat.android").exec();
                Shell.su("pm disable com.snapchat.android/com.snap.mushroom.MushroomMainActivity",
                        "pm disable com.snapchat.android/com.snap.mushroom.MainActivity",
                        "pm enable com.snapchat.android/.LandingPageActivity").exec();
                Toast.makeText(MainActivity.this, "All Done!", Toast.LENGTH_SHORT).show();
                setMushroomEnabled(false);
            }
        });

    }

    private void setMushroomEnabled(boolean enabled) {
        mushroomEnabled = enabled;
        launchButton.setEnabled(enabled);
        enableButton.setEnabled(!enabled);
        disableButton.setEnabled(enabled);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.github) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/JRoy/SC-Mushroom-Enabler")));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private List<String> readPrefs() {
        List<String> output = new ArrayList<>();
        Shell.su("cat /data/data/com.snapchat.android/shared_prefs/dynamicAppConfig.xml").to(output).exec();
        return output;
    }

    private void updatePrefs(List<String> lines) throws IOException {
        @SuppressLint("SdCardPath") SuFileOutputStream outputStream = new SuFileOutputStream(new SuFile("/data/data/com.snapchat.android/shared_prefs/dynamicAppConfig.xml"), false);
        for (String curLine : lines) {
            curLine = curLine + LINE_SEPARATOR;
            outputStream.write(curLine.getBytes());
        }
        outputStream.close();
    }

    private List<String> replaceList(List<String> list, String target, String replacement) {
        List<String> newList = new ArrayList<>();
        for (String curL : list) {
            newList.add(curL.replace(target, replacement));
        }
        return newList;
    }

    private String getAppFamily(List<String> list) {
        for (String curL : list) {
            if (curL.contains("appFamily")) {
                return curL.split("appFamily\">")[1].split("</string>")[0];
            }
        }
        return "null";
    }

    private int getPrevVer(List<String> list) {
        for (String curL : list) {
            if (curL.contains("previousAppVersion")) {
                return Integer.parseInt(curL.split("previousAppVersion\" value=\"")[1].split("\"")[0]);
            }
        }
        Toast.makeText(MainActivity.this, "Unable to find snapchat version!!! Did snap change their preferences?", Toast.LENGTH_SHORT).show();
        throw new IllegalStateException("Unable to find app version");
    }

    private boolean isInstalled() {
        for (PackageInfo curPackage : getPackageManager().getInstalledPackages(0)) {
            if ("com.snapchat.android".equals(curPackage.packageName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBeta() {
        for (PackageInfo curPackage : getPackageManager().getInstalledPackages(0)) {
            if ("com.snapchat.android".equals(curPackage.packageName)) {
                return curPackage.versionName.toLowerCase().contains("beta");
            }
        }
        return false;
    }
}
