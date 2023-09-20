/*
 * Copyright (C) 2018-2022 Tachibana General Laboratories, LLC
 * Copyright (C) 2018-2022 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * This file is part of Download Navi.
 *
 * Download Navi is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Download Navi is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Download Navi.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.tachibana.downloader.ui.main;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.h6ah4i.android.widget.advrecyclerview.animator.GeneralItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.animator.RefactoredDefaultItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.expandable.RecyclerViewExpandableItemManager;
import com.tachibana.downloader.R;
import com.tachibana.downloader.core.HttpConnection;
import com.tachibana.downloader.core.RepositoryHelper;
import com.tachibana.downloader.core.exception.HttpException;
import com.tachibana.downloader.core.exception.NormalizeUrlException;
import com.tachibana.downloader.core.model.DownloadEngine;
import com.tachibana.downloader.core.model.data.StatusCode;
import com.tachibana.downloader.core.model.data.entity.DownloadInfo;
import com.tachibana.downloader.core.model.data.entity.Header;
import com.tachibana.downloader.core.settings.SettingsRepository;
import com.tachibana.downloader.core.storage.DataRepository;
import com.tachibana.downloader.core.system.SystemFacadeHelper;
import com.tachibana.downloader.core.urlnormalizer.NormalizeUrl;
import com.tachibana.downloader.core.utils.DownloadUtils;
import com.tachibana.downloader.core.utils.MimeTypeUtils;
import com.tachibana.downloader.core.utils.Utils;
import com.tachibana.downloader.receiver.NotificationReceiver;
import com.tachibana.downloader.service.DownloadService;
import com.tachibana.downloader.ui.BaseAlertDialog;
import com.tachibana.downloader.ui.BatteryOptimizationDialog;
import com.tachibana.downloader.ui.PermissionDeniedDialog;
import com.tachibana.downloader.ui.adddownload.AddDownloadActivity;
import com.tachibana.downloader.ui.browser.BrowserActivity;
import com.tachibana.downloader.ui.main.drawer.DrawerExpandableAdapter;
import com.tachibana.downloader.ui.main.drawer.DrawerGroup;
import com.tachibana.downloader.ui.main.drawer.DrawerGroupItem;
import com.tachibana.downloader.ui.settings.SettingsActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class MainActivity extends AppCompatActivity {
    @SuppressWarnings("unused")
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String TAG_ABOUT_DIALOG = "about_dialog";
    private static final String TAG_PERM_DENIED_DIALOG = "perm_denied_dialog";
    private static final String TAG_BATTERY_DIALOG = "battery_dialog";
    protected CompositeDisposable disposables = new CompositeDisposable();
    /* Android data binding doesn't work with layout aliases */
    private CoordinatorLayout coordinatorLayout;
    private Toolbar toolbar;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ActionBarDrawerToggle toggle;
    private RecyclerView drawerItemsList;
    private LinearLayoutManager layoutManager;
    private DrawerExpandableAdapter drawerAdapter;
    private RecyclerView.Adapter wrappedDrawerAdapter;
    private RecyclerViewExpandableItemManager drawerItemManager;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private DownloadListPagerAdapter pagerAdapter;
    private DownloadsViewModel fragmentViewModel;
    private FloatingActionButton fab;
    private FloatingActionButton clear;
    private SearchView searchView;
    private DownloadEngine engine;
    private SettingsRepository pref;
    private BaseAlertDialog.SharedViewModel dialogViewModel;
    private BaseAlertDialog aboutDialog;
    private BaseAlertDialog deleteAllDownloadsDialog;
    private BaseAlertDialog exportDownloadsDialog;
    private BaseAlertDialog importDownloadsDialog;
    private PermissionDeniedDialog permDeniedDialog;
    private final ActivityResultLauncher<String> storagePermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (!isGranted && Utils.shouldRequestStoragePermission(this)) {
                    FragmentManager fm = getSupportFragmentManager();
                    if (fm.findFragmentByTag(TAG_PERM_DENIED_DIALOG) == null) {
                        permDeniedDialog = PermissionDeniedDialog.newInstance();
                        FragmentTransaction ft = fm.beginTransaction();
                        ft.add(permDeniedDialog, TAG_PERM_DENIED_DIALOG);
                        ft.commitAllowingStateLoss();
                    }
                }
            });
    private BatteryOptimizationDialog batteryDialog;
    private final List<HashMap<String, String>> exportSelected = new ArrayList<>();
    private final List<DownloadInfo> importSelected = new ArrayList<>();
    String writeContent = "";
    private List<CardView> exportList = new ArrayList<>();
    private HashMap<DownloadInfo, Boolean> shouldGetPaused = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(Utils.getAppTheme(getApplicationContext()));
        super.onCreate(savedInstanceState);

        if (getIntent().getAction() != null &&
                getIntent().getAction().equals(NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP)) {
            finish();
            return;
        }

        ViewModelProvider provider = new ViewModelProvider(this);
        fragmentViewModel = provider.get(DownloadsViewModel.class);
        dialogViewModel = provider.get(BaseAlertDialog.SharedViewModel.class);
        FragmentManager fm = getSupportFragmentManager();
        aboutDialog = (BaseAlertDialog) fm.findFragmentByTag(TAG_ABOUT_DIALOG);
        permDeniedDialog = (PermissionDeniedDialog) fm.findFragmentByTag(TAG_PERM_DENIED_DIALOG);
        batteryDialog = (BatteryOptimizationDialog) fm.findFragmentByTag(TAG_BATTERY_DIALOG);

        if (!Utils.checkStoragePermission(this) && permDeniedDialog == null) {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        setContentView(R.layout.activity_main);

        pref = RepositoryHelper.getSettingsRepository(getApplicationContext());
        Utils.disableBrowserFromSystem(getApplicationContext(), pref.browserDisableFromSystem());
        Utils.enableBrowserLauncherIcon(getApplicationContext(), pref.browserLauncherIcon());

        engine = DownloadEngine.getInstance(getApplicationContext());

        initLayout();
        engine.restoreDownloads();

        if (Utils.shouldShowBatteryOptimizationDialog(this)) {
            showBatteryOptimizationDialog();
        }
    }

    private void initLayout() {
        toolbar = findViewById(R.id.toolbar);
        coordinatorLayout = findViewById(R.id.coordinator);
        navigationView = findViewById(R.id.navigation_view);
        drawerLayout = findViewById(R.id.drawer_layout);
        tabLayout = findViewById(R.id.download_list_tabs);
        viewPager = findViewById(R.id.download_list_viewpager);
        fab = findViewById(R.id.add_fab);
        if (!pref.useDownloadDialog())
            fab.hide();
        clear = findViewById(R.id.purge_list);
        clear.hide();
        drawerItemsList = findViewById(R.id.drawer_items_list);
        layoutManager = new LinearLayoutManager(this);

        toolbar.setTitle(R.string.app_name);
        /* Disable elevation for portrait mode */
        if (!Utils.isTwoPane(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            toolbar.setElevation(0);
        setSupportActionBar(toolbar);

        if (drawerLayout != null) {
            toggle = new ActionBarDrawerToggle(this,
                    drawerLayout,
                    toolbar,
                    R.string.open_navigation_drawer,
                    R.string.close_navigation_drawer);
            drawerLayout.addDrawerListener(toggle);
        }
        initDrawer();
        fragmentViewModel.resetSearch();

        pagerAdapter = new DownloadListPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setOffscreenPageLimit(DownloadListPagerAdapter.NUM_FRAGMENTS);
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    switch (position) {
                        case DownloadListPagerAdapter.QUEUED_FRAG_POS:
                            tab.setText(R.string.fragment_title_queued);
                            break;
                        case DownloadListPagerAdapter.COMPLETED_FRAG_POS:
                            tab.setText(R.string.fragment_title_completed);
                            break;
                    }
                }
        ).attach();

        fab.setOnClickListener((v) -> startActivity(new Intent(this, AddDownloadActivity.class)));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == DownloadListPagerAdapter.COMPLETED_FRAG_POS)
                    clear.show();
                else
                    clear.hide();
                if (!pref.useDownloadDialog())
                    fab.hide();
                else
                    fab.show();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        clear.setOnClickListener((v) -> {
                deleteAllDownloadsDialog = BaseAlertDialog.newInstance(
                        getString(R.string.deleting),
                        getString(R.string.delete_all_downloads),
                        R.layout.dialog_delete_all_downloads,
                        getString(R.string.ok),
                        getString(R.string.cancel),
                        null,
                        false);
                var fm = getSupportFragmentManager();
                deleteAllDownloadsDialog.show(fm, "delete_all_downloads_dialog");
        });
    }

    private void initDrawer() {
        drawerItemManager = new RecyclerViewExpandableItemManager(null);
        drawerItemManager.setDefaultGroupsExpandedState(false);
        drawerItemManager.setOnGroupCollapseListener((groupPosition, fromUser, payload) -> {
            if (fromUser)
                saveGroupExpandState(groupPosition, false);
        });
        drawerItemManager.setOnGroupExpandListener((groupPosition, fromUser, payload) -> {
            if (fromUser)
                saveGroupExpandState(groupPosition, true);
        });
        GeneralItemAnimator animator = new RefactoredDefaultItemAnimator();
        /*
         * Change animations are enabled by default since support-v7-recyclerview v22.
         * Need to disable them when using animation indicator.
         */
        animator.setSupportsChangeAnimations(false);

        List<DrawerGroup> groups = Utils.getNavigationDrawerItems(this,
                PreferenceManager.getDefaultSharedPreferences(this));
        drawerAdapter = new DrawerExpandableAdapter(groups, drawerItemManager, this::onDrawerItemSelected);
        wrappedDrawerAdapter = drawerItemManager.createWrappedAdapter(drawerAdapter);
        onDrawerGroupsCreated();

        drawerItemsList.setLayoutManager(layoutManager);
        drawerItemsList.setAdapter(wrappedDrawerAdapter);
        drawerItemsList.setItemAnimator(animator);
        drawerItemsList.setHasFixedSize(false);

        drawerItemManager.attachRecyclerView(drawerItemsList);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        if (toggle != null)
            toggle.syncState();
    }

    @Override
    public void onStart() {
        super.onStart();

        subscribeAlertDialog();
        subscribeSettingsChanged();
    }

    @Override
    protected void onStop() {
        super.onStop();

        disposables.clear();
    }

    private void subscribeAlertDialog() {
        Disposable d = dialogViewModel.observeEvents()
                .subscribe((event) -> {
                    if (event.dialogTag == null) {
                        return;
                    }
                    if (event.dialogTag.equals(TAG_ABOUT_DIALOG)) {
                        switch (event.type) {
                            case NEGATIVE_BUTTON_CLICKED:
                                openChangelogLink();
                                break;
                            case DIALOG_SHOWN:
                                initAboutDialog();
                                break;
                        }
                    } else if (event.dialogTag.equals(TAG_PERM_DENIED_DIALOG)) {
                        if (event.type != BaseAlertDialog.EventType.DIALOG_SHOWN) {
                            permDeniedDialog.dismiss();
                        }
                        if (event.type == BaseAlertDialog.EventType.NEGATIVE_BUTTON_CLICKED) {
                            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                        }
                    } else if (event.dialogTag.equals(TAG_BATTERY_DIALOG)) {
                        if (event.type != BaseAlertDialog.EventType.DIALOG_SHOWN) {
                            batteryDialog.dismiss();
                            pref.askDisableBatteryOptimization(false);
                        }
                        if (event.type == BaseAlertDialog.EventType.POSITIVE_BUTTON_CLICKED) {
                            Utils.requestDisableBatteryOptimization(this);
                        }
                    } else if (event.dialogTag.equals("delete_all_downloads_dialog")) {
                        switch (event.type) {
                            case POSITIVE_BUTTON_CLICKED:
                                Context thisContext = this;
                                Thread thread = new Thread(() -> {
                                    DataRepository repo = RepositoryHelper.getDataRepository(thisContext);
                                    List<DownloadInfo> completedDownloads = new ArrayList<>();
                                    for (DownloadInfo downloadInfo : repo.getAllInfo()) {
                                        if (StatusCode.isStatusCompleted(downloadInfo.statusCode))
                                            completedDownloads.add(downloadInfo);
                                    }
                                    DownloadInfo[] downloadInfoArray = new DownloadInfo[completedDownloads.size()];
                                    engine.deleteDownloads(false, completedDownloads.toArray(downloadInfoArray));
                                });
                                thread.start();
                            case NEGATIVE_BUTTON_CLICKED:
                                deleteAllDownloadsDialog.dismiss();
                                break;
                        }
                    } else if (event.dialogTag.equals("export_all_downloads_dialog")) {
                        switch (event.type) {
                            case POSITIVE_BUTTON_CLICKED:
                                writeContent = "";
                                int i = 0;
                                writeContent += "#DownloadNavi\n";
                                for (HashMap<String, String> exportData : exportSelected) {
                                    CardView current = null;
                                    for (CardView cardView : exportList) {
                                        if (cardView.getTag().equals(exportData))
                                        {
                                            current = cardView;
                                            break;
                                        }
                                    }
                                    if (!((CheckBox) current.findViewById(R.id.includeInFile)).isChecked())
                                        continue;
                                    writeContent += exportData.get("startPaused") + ";" + exportData.get("filename") + ";" + exportData.get("url");
                                    if (i != (exportSelected.size() - 1))
                                        writeContent += "\n";
                                    i++;
                                }
                                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                                intent.addCategory(Intent.CATEGORY_OPENABLE);
                                intent.putExtra(Intent.EXTRA_TITLE, "download-navi-export.txt");
                                intent.setType("*/*");
                                startActivityForResult(intent, 512256);
                            case NEGATIVE_BUTTON_CLICKED:
                                exportDownloadsDialog.dismiss();
                                exportList = null;
                                break;
                        }
                    } else if (event.dialogTag.equals("import_downloads_dialog")) {
                        switch (event.type) {
                            case POSITIVE_BUTTON_CLICKED:
                                Context thisContext = this;
                                for (DownloadInfo downloadInfo : importSelected) {
//                                    if (shouldGetPaused.containsKey(downloadInfo))
//                                        if (shouldGetPaused.get(downloadInfo))
//                                            continue;
                                    ArrayList<Header> headers = new ArrayList<>();
                                    Thread thread = new Thread(() -> {
                                        var fetchedData = fetchDownloadData(downloadInfo.url, thisContext);
                                        if (fetchedData != null && !fetchedData.containsKey("error")) {
                                            // TODO: Probably should fill more variables
                                            // but this is enough to start download properly :)
                                            downloadInfo.mimeType = (String) fetchedData.get("mime");
                                            downloadInfo.totalBytes = (long) fetchedData.get("totalBytes");
                                            downloadInfo.partialSupport = (boolean) fetchedData.get("partialSupport");
                                        }
                                        DataRepository repo = RepositoryHelper.getDataRepository(thisContext);
                                        /* TODO: rewrite to WorkManager */
                                        /* Sync wait inserting */
                                        try {
                                            Thread t = new Thread(() -> {
                                                if (pref.replaceDuplicateDownloads())
                                                    repo.replaceInfoByUrl(downloadInfo, headers);
                                                else
                                                    repo.addInfo(downloadInfo, headers);
                                            });
                                            t.start();
                                            t.join();

                                        } catch (InterruptedException e) {
                                            return;
                                        }
                                        engine.runDownload(downloadInfo);
                                    });
                                    thread.start();
                                }
                            case NEGATIVE_BUTTON_CLICKED:
                                importDownloadsDialog.dismiss();
                                importSelected.clear();
                                shouldGetPaused.clear();
                                break;
                        }
                    }
                });
        disposables.add(d);
    }

    public static void justAddTheDamnDownload(String url, Uri pathAsUri, String filename, SettingsRepository pref, Context thisContext, DownloadEngine engine) {
//        Uri pathAsUri = Uri.fromFile(new File(path));

        if (filename == null) {
            String fileName = "";

            int queryIndex = url.indexOf('?');
            /* If there is a query string strip it, same as desktop browsers */
            if (queryIndex > 0)
                url = url.substring(0, queryIndex);

            if (!url.endsWith("/")) {
                int index = url.lastIndexOf('/') + 1;
                if (index > 0) {
                    String rawFilename = url.substring(index);
                    fileName = DownloadUtils.autoDecodePercentEncoding(rawFilename);
                    if (fileName == null) {
                        fileName = rawFilename;
                    }
                }
            }

            // makes sense
            filename = fileName;
        }

        DownloadInfo info = new DownloadInfo(pathAsUri, url, filename);
        info.userAgent = pref.userAgent();
        info.dateAdded = System.currentTimeMillis();

        ArrayList<Header> headers = new ArrayList<>();
//        Context thisContext = this;
        Thread thread = new Thread(() -> {
            var fetchedData = fetchDownloadData(info.url, thisContext);
//            try {
//                TimeUnit.SECONDS.sleep(1);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
            if (fetchedData != null && !fetchedData.containsKey("error")) {
                // TODO: Probably should fill more variables
                // but this is enough to start download properly :)
                info.mimeType = (String) fetchedData.get("mime");
                info.totalBytes = (long) fetchedData.get("totalBytes");
                info.partialSupport = (boolean) fetchedData.get("partialSupport");
            }
//            else if (fetchedData != null && fetchedData.containsKey("error")) {
//                return;
//            }
            DataRepository repo = RepositoryHelper.getDataRepository(thisContext);
            /* TODO: rewrite to WorkManager */
            /* Sync wait inserting */
            try {
                Thread t = new Thread(() -> {
                    if (pref.replaceDuplicateDownloads())
                        repo.replaceInfoByUrl(info, headers);
                    else
                        repo.addInfo(info, headers);
                });
                t.start();
                t.join();

            } catch (InterruptedException e) {
                return;
            }
            engine.runDownload(info);
        });
        thread.start();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 512256 && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            try {
                OutputStream output = this.getContentResolver().openOutputStream(uri);
                output.write(writeContent.getBytes());
                output.flush();
                output.close();
                writeContent = "";
            } catch (IOException e) {
                Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
            }
        }
        else if (resultCode == 512256)
            writeContent = "";
        if (requestCode == 256512 && resultCode == Activity.RESULT_OK) {
            try {
                String fullData = readTextFromUri(data.getData());

                importDownloadsDialog = BaseAlertDialog.newInstance(
                        "Import",
                        "The following downloads will be imported and automatically started.",
                        R.layout.dialog_export_downloads,
                        getString(R.string.ok),
                        getString(R.string.cancel),
                        null,
                        false);
                var fm = getSupportFragmentManager();
                importDownloadsDialog.show(fm, "import_downloads_dialog");
                List<HashMap<String, String>> fullDataList = new ArrayList<>();
                shouldGetPaused.clear();
                importSelected.clear();
                Context thisContext = this;
                Thread thread = new Thread(() -> {
                    try {
                        // TODO: there is probably better way to do this
                        TimeUnit.SECONDS.sleep(1);

                        var dialog = importDownloadsDialog.getDialog();
                        LinearLayout exportDownloadsList = (LinearLayout) dialog.findViewById(R.id.exportDownloadsList);
                        List<CardView> downloads = new ArrayList<>();
                        LayoutInflater i = LayoutInflater.from(thisContext);
                        DataRepository repo = RepositoryHelper.getDataRepository(thisContext);

                        List<DownloadInfo> generatedDownloadInfo = new ArrayList<>();
                        String[] lines = splitNonRegex(fullData, "\n").toArray(new String[0]);
                        boolean headerOk = lines[0].equals("#DownloadNavi");
//                        var lines = .toArray();
                        for (int i1 = 0; i1 < lines.length; i1++) {
                            var line = lines[i1];
                            if (line.equals(""))
                                continue;
                            // checking if line is valid
//                            var splitLine = line.split(Pattern.quote(";"));
                            var currentData = new HashMap<String, String>();
                            if (!headerOk)
                            {
                                runOnUiThread(() -> exportDownloadsList.findViewById(R.id.downloadNoHeader).setVisibility(View.VISIBLE));
                            }
                            if (headerOk) {
                                var splitLine = splitNonRegex(line, ";").toArray(new String[0]);
                                if (splitLine.length > 1) {
                                    // valid?
                                    currentData.put("startPaused", splitLine[0]);
                                    currentData.put("filename", splitLine[1]);
                                    currentData.put("url", splitLine[2]);
                                } else {
                                    // invalid?
                                    continue;
                                }
                            }
                            else {
                                boolean successFirstTry = false;
                                boolean successSecondTry = false;
                                var splitLine = splitNonRegex(line, ";").toArray(new String[0]);
                                try {
                                    new java.net.URL(line);
                                    successFirstTry = true;
                                } catch (MalformedURLException e) {
                                    e.printStackTrace();
                                    if (splitLine.length > 1) {
                                        successSecondTry = true;
                                    }
                                }
                                if (successFirstTry) {
                                    String finalFilename = "";

                                    int queryIndex = line.indexOf('?');
                                    /* If there is a query string strip it, same as desktop browsers */
                                    if (queryIndex > 0)
                                        line = line.substring(0, queryIndex);

                                    if (!line.endsWith("/")) {
                                        int index = line.lastIndexOf('/') + 1;
                                        if (index > 0) {
                                            String rawFilename = line.substring(index);
                                            finalFilename = DownloadUtils.autoDecodePercentEncoding(rawFilename);
                                            if (finalFilename == null) {
                                                finalFilename = rawFilename;
                                            }
                                        }
                                    }

                                    currentData.put("startPaused", String.valueOf(false));
                                    currentData.put("filename", finalFilename);
                                    currentData.put("url", line);
                                } else if (successSecondTry) {
                                    currentData.put("startPaused", splitLine[0]);
                                    currentData.put("filename", splitLine[1]);
                                    currentData.put("url", splitLine[2]);
                                } else
                                    continue;
                            }
                            fullDataList.add(currentData);
                        }

                        // creating download info
                        for (HashMap<String, String> dataMap : fullDataList) {
                            SettingsRepository pref = RepositoryHelper.getSettingsRepository(getApplicationContext());

                            // should be saved with export file, but uhhh
                            Uri dirPath = Uri.parse(pref.saveDownloadsIn());

                            var url = dataMap.get("url");
                            var fileName = dataMap.get("filename");
                            var startPaused = dataMap.get("startPaused");

                            DownloadInfo info = new DownloadInfo(dirPath, url, fileName);
                            info.userAgent = pref.userAgent();

                            info.dateAdded = System.currentTimeMillis();
                            shouldGetPaused.put(info, Boolean.valueOf(startPaused));
                            generatedDownloadInfo.add(info);
                        }

//                        ArrayList<Header> headers = new ArrayList<>();
//                        headers.add(new Header(info.id, "ETag", params.getEtag()));
//                        if (!TextUtils.isEmpty(params.getReferer())) {
//                            headers.add(new Header(info.id, "Referer", params.getReferer()));
//                        }
//
//                        /* TODO: rewrite to WorkManager */
//                        /* Sync wait inserting */
//                        try {
//                            Thread t = new Thread(() -> {
//                                if (pref.replaceDuplicateDownloads())
//                                    repo.replaceInfoByUrl(info, headers);
//                                else
//                                    repo.addInfo(info, headers);
//                            });
//                            t.start();
//                            t.join();
//
//                        } catch (InterruptedException e) {
//                            return;
//                        }
//
//                        engine.runDownload(info);

                        for (DownloadInfo downloadInfo : generatedDownloadInfo) {
                            //                    LinearLayout ll = (LinearLayout) getLayoutInflater().from(getApplicationContext()).inflate(R.layout.item_download_export_list, exportDownloadsList, false);
                            //                    LinearLayout ll = (LinearLayout) DataBindingUtil.inflate(i, R.layout.item_download_export_list, null, false).getRoot();
                            CardView ll = (CardView) i.inflate(R.layout.item_download_export_list, null, false);
                            downloads.add(ll);
                            // fancy lambda
                            runOnUiThread(() -> exportDownloadsList.addView(ll));
                            var current = downloads.get(downloads.indexOf(ll));
//                            ((CheckBox) current.findViewById(R.id.includeInFile)).setChecked(true);
//                            ((CheckBox) current.findViewById(R.id.includeInFile)).setText("Include in import");
//                            ((CheckBox) current.findViewById(R.id.startPaused)).setChecked(!StatusCode.isStatusCompleted(downloadInfo.statusCode));
                            runOnUiThread(() -> current.findViewById(R.id.startPaused).setVisibility(View.GONE));
                            runOnUiThread(() -> current.findViewById(R.id.includeInFile).setVisibility(View.GONE));
                            ((TextView) current.findViewById(R.id.filename)).setText(downloadInfo.fileName);
                            String hostname = Utils.getHostFromUrl(downloadInfo.url);
                            ((TextView) current.findViewById(R.id.status)).setText(thisContext.getString(R.string.download_finished_template,
                                    (hostname == null ? "" : hostname),
                                    (downloadInfo.totalBytes == -1 ? thisContext.getString(R.string.not_available) :
                                            Formatter.formatFileSize(thisContext, downloadInfo.totalBytes))));

//                            engine.runDownload(downloadInfo);
                            importSelected.add(downloadInfo);
                        }
//                        exportList = downloads;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
                thread.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // i've got headache by using regex
    public static List<String> splitNonRegex(String input, String delim) {
        List<String> l = new ArrayList<String>();
        int offset = 0;

        while (true) {
            int index = input.indexOf(delim, offset);
            if (index == -1) {
                l.add(input.substring(offset));
                return l;
            } else {
                l.add(input.substring(offset, index));
                offset = (index + delim.length());
            }
        }
    }

    private String readTextFromUri(Uri uri) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream =
                     getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(Objects.requireNonNull(inputStream)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append('\n');
            }
        }
        return stringBuilder.toString();
    }


    private void showBatteryOptimizationDialog() {
        var fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(TAG_BATTERY_DIALOG) == null) {
            batteryDialog = BatteryOptimizationDialog.newInstance();
            var ft = fm.beginTransaction();
            ft.add(batteryDialog, TAG_BATTERY_DIALOG);
            ft.commitAllowingStateLoss();
        }
    }

    private void subscribeSettingsChanged() {
        invalidateOptionsMenu();
        disposables.add(pref.observeSettingsChanged()
                .subscribe((key) -> {
                    if (key.equals(getString(R.string.pref_key_browser_hide_menu_icon))) {
                        invalidateOptionsMenu();
                    }
                }));
    }

    private void onDrawerGroupsCreated() {
        for (int pos = 0; pos < drawerAdapter.getGroupCount(); pos++) {
            DrawerGroup group = drawerAdapter.getGroup(pos);
            if (group == null)
                return;

            Resources res = getResources();
            if (group.id == res.getInteger(R.integer.drawer_category_id)) {
                fragmentViewModel.setCategoryFilter(
                        Utils.getDrawerGroupCategoryFilter(this, group.getSelectedItemId()), false);

            } else if (group.id == res.getInteger(R.integer.drawer_status_id)) {
                fragmentViewModel.setStatusFilter(
                        Utils.getDrawerGroupStatusFilter(this, group.getSelectedItemId()), false);

            } else if (group.id == res.getInteger(R.integer.drawer_date_added_id)) {
                fragmentViewModel.setDateAddedFilter(
                        Utils.getDrawerGroupDateAddedFilter(this, group.getSelectedItemId()), false);

            } else if (group.id == res.getInteger(R.integer.drawer_sorting_id)) {
                fragmentViewModel.setSort(Utils.getDrawerGroupItemSorting(this, group.getSelectedItemId()), false);
            }

            applyExpandState(group, pos);
        }
    }

    private void applyExpandState(DrawerGroup group, int pos) {
        if (group.getDefaultExpandState())
            drawerItemManager.expandGroup(pos);
        else
            drawerItemManager.collapseGroup(pos);
    }

    private void saveGroupExpandState(int groupPosition, boolean expanded) {
        DrawerGroup group = drawerAdapter.getGroup(groupPosition);
        if (group == null)
            return;

        Resources res = getResources();
        String prefKey = null;
        if (group.id == res.getInteger(R.integer.drawer_category_id))
            prefKey = getString(R.string.drawer_category_is_expanded);

        else if (group.id == res.getInteger(R.integer.drawer_status_id))
            prefKey = getString(R.string.drawer_status_is_expanded);

        else if (group.id == res.getInteger(R.integer.drawer_date_added_id))
            prefKey = getString(R.string.drawer_time_is_expanded);

        else if (group.id == res.getInteger(R.integer.drawer_sorting_id))
            prefKey = getString(R.string.drawer_sorting_is_expanded);

        if (prefKey != null)
            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putBoolean(prefKey, expanded)
                    .apply();
    }

    private void onDrawerItemSelected(DrawerGroup group, DrawerGroupItem item) {
        Resources res = getResources();
        String prefKey = null;
        if (group.id == res.getInteger(R.integer.drawer_category_id)) {
            prefKey = getString(R.string.drawer_category_selected_item);
            fragmentViewModel.setCategoryFilter(
                    Utils.getDrawerGroupCategoryFilter(this, item.id), true);

        } else if (group.id == res.getInteger(R.integer.drawer_status_id)) {
            prefKey = getString(R.string.drawer_status_selected_item);
            fragmentViewModel.setStatusFilter(
                    Utils.getDrawerGroupStatusFilter(this, item.id), true);

        } else if (group.id == res.getInteger(R.integer.drawer_date_added_id)) {
            prefKey = getString(R.string.drawer_time_selected_item);
            fragmentViewModel.setDateAddedFilter(
                    Utils.getDrawerGroupDateAddedFilter(this, item.id), true);

        } else if (group.id == res.getInteger(R.integer.drawer_sorting_id)) {
            prefKey = getString(R.string.drawer_sorting_selected_item);
            fragmentViewModel.setSort(Utils.getDrawerGroupItemSorting(this, item.id), true);
        }

        if (prefKey != null)
            saveSelectionState(prefKey, item);

        if (drawerLayout != null)
            drawerLayout.closeDrawer(GravityCompat.START);
    }

    private void saveSelectionState(String prefKey, DrawerGroupItem item) {
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putLong(prefKey, item.id)
                .apply();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        searchView = (SearchView) menu.findItem(R.id.search).getActionView();
        initSearch();

        return true;
    }

    private void initSearch() {
        searchView.setMaxWidth(Integer.MAX_VALUE);
        searchView.setOnCloseListener(() -> {
            fragmentViewModel.resetSearch();

            return false;
        });
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                fragmentViewModel.setSearchQuery(query);
                /* Submit the search will hide the keyboard */
                searchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                fragmentViewModel.setSearchQuery(newText);

                return true;
            }
        });
        searchView.setQueryHint(getString(R.string.search));
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        /* Assumes current activity is the searchable activity */
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.browser_menu).setVisible(!pref.browserHideMenuIcon());

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.pause_all_menu) {
            pauseAll();
        } else if (itemId == R.id.resume_all_menu) {
            resumeAll();
        } else if (itemId == R.id.settings_menu) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (itemId == R.id.about_menu) {
            showAboutDialog();
        } else if (itemId == R.id.shutdown_app_menu) {
            closeOptionsMenu();
            shutdown();
        } else if (itemId == R.id.import_menu) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            intent.setType("*/*");

            startActivityForResult(intent, 256512);
        } else if (itemId == R.id.export_menu) {
            exportDownloadsDialog = BaseAlertDialog.newInstance(
                    "Export",
                    "",
                    R.layout.dialog_export_downloads,
                    getString(R.string.ok),
                    getString(R.string.cancel),
                    null,
                    false);
            var fm = getSupportFragmentManager();
            exportDownloadsDialog.show(fm, "export_all_downloads_dialog");
            exportSelected.clear();
//            fm.executePendingTransactions();
//            exportDownloadsDialog.getView().get
            Context thisContext = this;
            Thread thread = new Thread(() -> {
                try {
                    // TODO: there is probably better way to do this
                    TimeUnit.SECONDS.sleep(1);
//                    TimeUnit.SECONDS.sleep((long) 0.5);
                    DataRepository repo = RepositoryHelper.getDataRepository(thisContext);
                    List<DownloadInfo> completedDownloads = new ArrayList<>();
                    var dialog = exportDownloadsDialog.getDialog();
        //            LinearLayout exportDownloadsList = findViewById(R.id.exportDownloadsList);
                    LinearLayout exportDownloadsList = (LinearLayout) dialog.findViewById(R.id.exportDownloadsList);
//                    LinearLayout exportDownloadsList = (LinearLayout) exportDownloadsDialog.getView();
                    List<CardView> downloads = new ArrayList<>();
                    LayoutInflater i = LayoutInflater.from(thisContext);
                    for (DownloadInfo downloadInfo : repo.getAllInfo()) {
    //                    LinearLayout ll = (LinearLayout) getLayoutInflater().from(getApplicationContext()).inflate(R.layout.item_download_export_list, exportDownloadsList, false);
    //                    LinearLayout ll = (LinearLayout) DataBindingUtil.inflate(i, R.layout.item_download_export_list, null, false).getRoot();
                        CardView ll = (CardView) i.inflate(R.layout.item_download_export_list, null, false);
                        downloads.add(ll);
                        // fancy lambda
                        runOnUiThread(() -> exportDownloadsList.addView(ll));
                        var current = downloads.get(downloads.indexOf(ll));
                        ((CheckBox) current.findViewById(R.id.includeInFile)).setChecked(true);
                        ((CheckBox) current.findViewById(R.id.startPaused)).setChecked(!StatusCode.isStatusCompleted(downloadInfo.statusCode));
                        ((TextView) current.findViewById(R.id.filename)).setText(downloadInfo.fileName);
                        String hostname = Utils.getHostFromUrl(downloadInfo.url);
                        ((TextView) current.findViewById(R.id.status)).setText(thisContext.getString(R.string.download_finished_template,
                                (hostname == null ? "" : hostname),
                                (downloadInfo.totalBytes == -1 ? thisContext.getString(R.string.not_available) :
                                        Formatter.formatFileSize(thisContext, downloadInfo.totalBytes))));
                        var data = new HashMap<String, String>();
                        data.put("startPaused", String.valueOf(!StatusCode.isStatusCompleted(downloadInfo.statusCode)));
                        data.put("filename", downloadInfo.fileName);
                        data.put("url", downloadInfo.url);
                        data.put("id", String.valueOf(exportSelected.size()));
                        current.setTag(data);
                        exportSelected.add(data);
                    }
                    exportList = downloads;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
            thread.start();

        } else if (itemId == R.id.browser_menu) {
            startActivity(new Intent(this, BrowserActivity.class));
        }

        return true;
    }

    public static HashMap<String, Object> fetchDownloadData(String url, Context thisContext)
    {
        final String[] finalUrl = {url};
//        SettingsRepository pref = RepositoryHelper.getSettingsRepository(getApplicationContext());
        SettingsRepository pref = RepositoryHelper.getSettingsRepository(thisContext);
        final boolean[] connectWithReferer = new boolean[] {false};

//        HttpConnection connection;
//        try {
//            connection = new HttpConnection(url);
//        } catch (MalformedURLException | GeneralSecurityException e) {
//            e.printStackTrace();
//            return null;
//        }
//        connection.setTimeout(800);
//        connection.setReferer(url);
//        connection.contentRangeLength(true);
//
//        NetworkInfo netInfo = SystemFacadeHelper.getSystemFacade(this).getActiveNetworkInfo();
//        if (netInfo == null || !netInfo.isConnected())
////            return new ConnectException("Network is disconnected");
//            return null;
//
//        connection.setListener(new HttpConnection.Listener() {
//            @Override
//            public void onConnectionCreated(HttpURLConnection conn)
//            {
//                String userAgent = pref.userAgent();
//                if (conn.getRequestProperty("User-Agent") == null && !TextUtils.isEmpty(userAgent)) {
//                    conn.addRequestProperty("User-Agent", userAgent);
//                }
//            }
//
//            @Override
//            public void onResponseHandle(HttpURLConnection conn, int code, String message)
//            {
//                if (code == HttpURLConnection.HTTP_OK ||
//                        code == HttpURLConnection.HTTP_PARTIAL) {
//                    connectWithReferer[0] = viewModel.get()
//                            .parseOkHeaders(conn, connectWithReferer[0]);
//                } else {
//                    err[0] = new HttpException("Failed to fetch link, response code: " + code, code);
//                }
//            }
//
//            @Override
//            public void onMoved(String newUrl, boolean permanently)
//            {
//                if (viewModel.get() == null)
//                    return;
//
//                try {
//                    viewModel.get().params.setUrl(NormalizeUrl.normalize(newUrl));
//
//                } catch (NormalizeUrlException e) {
//                    err[0] = e;
//                }
//            }
//
//            @Override
//            public void onIOException(IOException e)
//            {
//                err[0] = e;
//            }
//
//            @Override
//            public void onTooManyRedirects()
//            {
//                err[0] = new HttpException("Too many redirects");
//            }
//        });
//        connection.run();
        HashMap<String, Object> fakeParams = new HashMap<>();
        fakeParams.put("url", url);
        do {
            HttpConnection connection;
            try {
                connection = new HttpConnection(finalUrl[0]);
            } catch (Exception e) {
                return null;
            }
            connection.setTimeout(10000);
            connection.setReferer(
                    finalUrl[0]
            );
            connection.contentRangeLength(true);

            NetworkInfo netInfo = SystemFacadeHelper.getSystemFacade(thisContext).getActiveNetworkInfo();
            if (netInfo == null || !netInfo.isConnected())
                return null;

            connection.setListener(new HttpConnection.Listener() {
                @Override
                public void onConnectionCreated(HttpURLConnection conn)
                {
                    String userAgent = pref.userAgent();
                    if (conn.getRequestProperty("User-Agent") == null && !TextUtils.isEmpty(userAgent)) {
                        conn.addRequestProperty("User-Agent", userAgent);
                    }
                }

                @Override
                public void onResponseHandle(HttpURLConnection conn, int code, String message)
                {
                    if (code == HttpURLConnection.HTTP_OK ||
                            code == HttpURLConnection.HTTP_PARTIAL) {
                        connectWithReferer[0] = parseOkHeadersStandalone(conn, connectWithReferer[0], fakeParams, thisContext);
                    } else {
//                        err[0] = new HttpException("Failed to fetch link, response code: " + code, code);
                        fakeParams.put("error", new HttpException("Failed to fetch link, response code: " + code, code));
                    }
                }

                @Override
                public void onMoved(String newUrl, boolean permanently)
                {
                    try {
                        finalUrl[0] = NormalizeUrl.normalize(newUrl);

                    } catch (NormalizeUrlException e) {
//                        err[0] = e;
                        fakeParams.put("error", e);
                    }
                }

                @Override
                public void onIOException(IOException e)
                {
//                    err[0] = e;
                    fakeParams.put("error", e);
                }

                @Override
                public void onTooManyRedirects()
                {
//                    err[0] = new HttpException("Too many redirects");
                    fakeParams.put("error", new HttpException("Too many redirects"));
                }
            });
            connection.run();

        } while (connectWithReferer[0]);
        return fakeParams;
    }

    private static boolean parseOkHeadersStandalone(HttpURLConnection conn, boolean needsRefererPrevValue, HashMap<String, Object> params, Context thisContext)
    {
        String contentDisposition = conn.getHeaderField("Content-Disposition");
        String contentLocation = conn.getHeaderField("Content-Location");
        String tmpUrl = conn.getURL().toString();

        String mimeType = Intent.normalizeMimeType(conn.getContentType());
        /* Try to determine the MIME type later by the filename extension */
        if ("application/octet-stream".equals(mimeType))
            mimeType = null;

//        String fileName = DownloadUtils.getHttpFileName(
//                fs,
//                tmpUrl,
//                contentDisposition,
//                contentLocation,
//                mimeType
//        );

        String fileName = "";

        int queryIndex = tmpUrl.indexOf('?');
        /* If there is a query string strip it, same as desktop browsers */
        if (queryIndex > 0)
            tmpUrl = tmpUrl.substring(0, queryIndex);

        if (!tmpUrl.endsWith("/")) {
            int index = tmpUrl.lastIndexOf('/') + 1;
            if (index > 0) {
                String rawFilename = tmpUrl.substring(index);
                fileName = DownloadUtils.autoDecodePercentEncoding(rawFilename);
                if (fileName == null) {
                    fileName = rawFilename;
                }
            }
        }

        /* Try to get MIME from filename extension */
        if (mimeType == null) {
            String extension = SystemFacadeHelper.getFileSystemFacade(thisContext).getExtension(fileName);
            if (!TextUtils.isEmpty(extension))
                mimeType = MimeTypeUtils.getMimeTypeFromExtension(extension);
        }
        boolean currentRefererEmpty = TextUtils.isEmpty((CharSequence) params.get("referer"));
//        boolean currentRefererEmpty = TextUtils.isEmpty(params.getReferer());
//        boolean currentRefererEmpty = true;
        boolean needsReferer = currentRefererEmpty &&
                Utils.needsReferer(mimeType, SystemFacadeHelper.getFileSystemFacade(thisContext).getExtension(fileName));
        if (needsReferer && !needsRefererPrevValue) {
            return true;
        } else if (!needsReferer && needsRefererPrevValue) {
            if (currentRefererEmpty) {
                params.put("referer", params.get("url"));
            }
        }

        params.put("filename", fileName);
        if (mimeType != null)
            params.put("mime", mimeType);

        params.put("ETag", conn.getHeaderField("ETag"));
        final String transferEncoding = conn.getHeaderField("Transfer-Encoding");
        if (transferEncoding == null) {
            try {
                params.put("totalBytes", Long.parseLong(conn.getHeaderField("Content-Length")));
            } catch (NumberFormatException e) {
                params.put("totalBytes", -1);
            }
        } else {
            params.put("totalBytes", -1);
        }
        if ((long) params.get("totalBytes") == -1) {
            var bytes = DownloadUtils.parseContentRangeFullSize(
                    conn.getHeaderField("Content-Range")
            );
            params.put("totalBytes", bytes);
        }
        params.put("partialSupport",
                "bytes".equalsIgnoreCase(conn.getHeaderField("Accept-Ranges")) ||
                        conn.getHeaderField("Content-Range") != null
        );

        /* The number of pieces can't be more than the number of bytes */
        long total = (long) params.get("totalBytes");
//        if (total > 0)
//            maxNumPieces.set(total < maxNumPieces.get() ? (int)total : DownloadInfo.MAX_PIECES);

        return false;
    }

    private void pauseAll() {
        engine.pauseAllDownloads();
    }

    private void resumeAll() {
        engine.resumeDownloads(false);
    }

    private void showAboutDialog() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(TAG_ABOUT_DIALOG) == null) {
            aboutDialog = BaseAlertDialog.newInstance(
                    getString(R.string.about_title),
                    null,
                    R.layout.dialog_about,
                    getString(R.string.ok),
                    getString(R.string.about_changelog),
                    null,
                    true);
            aboutDialog.show(fm, TAG_ABOUT_DIALOG);
        }
    }

    private void initAboutDialog() {
        if (aboutDialog == null)
            return;

        Dialog dialog = aboutDialog.getDialog();
        if (dialog != null) {
            TextView versionTextView = dialog.findViewById(R.id.about_version);
            TextView descriptionTextView = dialog.findViewById(R.id.about_description);
            String versionName = Utils.getAppVersionName(this);
            if (versionName != null)
                versionTextView.setText(versionName);
            descriptionTextView.setText(Html.fromHtml(getString(R.string.about_description)));
            descriptionTextView.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    private void openChangelogLink() {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(getString(R.string.about_changelog_link)));
        startActivity(i);
    }

    public void shutdown() {
        Intent i = new Intent(getApplicationContext(), DownloadService.class);
        i.setAction(DownloadService.ACTION_SHUTDOWN);
        startService(i);
        finish();
    }
}
