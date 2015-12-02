package com.habitrpg.android.habitica;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;
import com.habitrpg.android.habitica.callbacks.HabitRPGUserCallback;
import com.habitrpg.android.habitica.events.OldTaskRemovedEvent;
import com.habitrpg.android.habitica.events.commands.OpenGemPurchaseFragmentCommand;
import com.habitrpg.android.habitica.prefs.PrefsActivity;
import com.habitrpg.android.habitica.ui.AvatarWithBarsViewModel;
import com.habitrpg.android.habitica.ui.MainDrawerBuilder;
import com.habitrpg.android.habitica.ui.fragments.BaseFragment;
import com.habitrpg.android.habitica.userpicture.UserPicture;
import com.habitrpg.android.habitica.userpicture.UserPictureRunnable;
import com.instabug.wrapper.support.activity.InstabugAppCompatActivity;
import com.magicmicky.habitrpgwrapper.lib.models.HabitRPGUser;
import com.magicmicky.habitrpgwrapper.lib.models.tasks.ChecklistItem;
import com.magicmicky.habitrpgwrapper.lib.models.tasks.Days;
import com.magicmicky.habitrpgwrapper.lib.models.tasks.Task;
import com.magicmicky.habitrpgwrapper.lib.models.tasks.TaskTag;
import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.model.interfaces.IProfile;
import com.raizlabs.android.dbflow.runtime.transaction.BaseTransaction;
import com.raizlabs.android.dbflow.runtime.transaction.TransactionListener;
import com.raizlabs.android.dbflow.sql.builder.Condition;
import com.raizlabs.android.dbflow.sql.language.Delete;
import com.raizlabs.android.dbflow.sql.language.Select;

import org.solovyev.android.checkout.ActivityCheckout;
import org.solovyev.android.checkout.Checkout;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import butterknife.ButterKnife;
import butterknife.InjectView;
import de.greenrobot.event.EventBus;
import io.fabric.sdk.android.Fabric;

public class MainActivity extends InstabugAppCompatActivity implements HabitRPGUserCallback.OnUserReceived {

    public enum SnackbarDisplayType {
        NORMAL, FAILURE, DROP
    }

    BaseFragment activeFragment;

    @InjectView(R.id.floating_menu_wrapper)
    FrameLayout floatingMenuWrapper;

    @InjectView(R.id.toolbar)
    Toolbar toolbar;

    @InjectView(R.id.detail_tabs)
    TabLayout detail_tabs;

    @InjectView(R.id.avatar_with_bars)
    View avatar_with_bars;

    AccountHeader accountHeader;
    public Drawer drawer;

    protected HostConfig hostConfig;
    protected HabitRPGUser user;

    AvatarWithBarsViewModel avatarInHeader;

    APIHelper mAPIHelper;

    // Checkout needs to be in the Activity..
    public static ActivityCheckout checkout = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inject Controls
        ButterKnife.inject(this);

        // Initialize Crashlytics
        Crashlytics crashlytics = new Crashlytics.Builder()
                .core(new CrashlyticsCore.Builder().disabled(BuildConfig.DEBUG).build())
                .build();
        Fabric.with(this, crashlytics);

        this.hostConfig = PrefsActivity.fromContext(this);
        HabiticaApplication.checkUserAuthentication(this, hostConfig);
        HabiticaApplication.ApiHelper = this.mAPIHelper = new APIHelper(this, hostConfig);

        new Select().from(HabitRPGUser.class).where(Condition.column("id").eq(hostConfig.getUser())).async().querySingle(userTransactionListener);


        if (toolbar != null) {
            setSupportActionBar(toolbar);

            ActionBar actionBar = getSupportActionBar();

            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setDisplayShowHomeEnabled(false);
                actionBar.setDisplayShowTitleEnabled(true);
                actionBar.setDisplayUseLogoEnabled(false);
                actionBar.setHomeButtonEnabled(false);
            }

        }

        avatarInHeader = new AvatarWithBarsViewModel(this, avatar_with_bars);
        accountHeader = MainDrawerBuilder.CreateDefaultAccountHeader(this).build();
        drawer = MainDrawerBuilder.CreateDefaultBuilderSettings(this, toolbar, accountHeader)
                .build();

        drawer.setSelectionAtPosition(1);

        // Create Checkout

        checkout = Checkout.forActivity(this, HabiticaApplication.Instance.getCheckout());

        checkout.start();

        EventBus.getDefault().register(this);
    }


    private void saveLoginInformation() {
        HabiticaApplication.User = user;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        boolean ans = editor.putString(getString(R.string.SP_username), user.getAuthentication().getLocalAuthentication().getUsername())
                .putString(getString(R.string.SP_email), user.getAuthentication().getLocalAuthentication().getEmail())
                .commit();
        try {
            if (!ans) {
                throw new Exception("Shared Preferences Username and Email error");
            }
        } catch (Exception e) {
            Log.e("SHARED PREFERENCES", e.getMessage());
        }
    }

    public void onEvent(OpenGemPurchaseFragmentCommand cmd) {
        drawer.setSelection(MainDrawerBuilder.SIDEBAR_PURCHASE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    public void displayFragment(BaseFragment fragment) {
        fragment.setArguments(getIntent().getExtras());
        fragment.mAPIHelper = mAPIHelper;
        fragment.setUser(user);
        fragment.setActivity(this);
        fragment.setTabLayout(detail_tabs);
        fragment.setFloatingMenuWrapper(floatingMenuWrapper);

        if (getSupportFragmentManager().getFragments() == null) {
            getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, fragment).commit();
        } else {
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).addToBackStack(null).commit();
        }
    }

    private TransactionListener<HabitRPGUser> userTransactionListener = new TransactionListener<HabitRPGUser>() {
        @Override
        public void onResultReceived(HabitRPGUser habitRPGUser) {
            user = habitRPGUser;
            setUserData(true);
        }

        @Override
        public boolean onReady(BaseTransaction<HabitRPGUser> baseTransaction) {
            return true;
        }

        @Override
        public boolean hasResult(BaseTransaction<HabitRPGUser> baseTransaction, HabitRPGUser habitRPGUser) {
            return true;
        }
    };

    private void setUserData(boolean fromLocalDb) {
        if (user != null) {
            Calendar mCalendar = new GregorianCalendar();
            TimeZone mTimeZone = mCalendar.getTimeZone();
            long offset = -TimeUnit.MINUTES.convert(mTimeZone.getRawOffset(), TimeUnit.MILLISECONDS);
            if (offset != user.getPreferences().getTimezoneOffset()) {
                Map<String, String> updateData = new HashMap<String, String>();
                updateData.put("preferences.timezoneOffset", String.valueOf(offset));
                mAPIHelper.apiService.updateUser(updateData, new HabitRPGUserCallback(this));
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateHeader();
                    updateSidebar();
                    saveLoginInformation();
                    if (activeFragment != null) {
                        activeFragment.updateUserData(user);
                    }
                }
            });

            if (!fromLocalDb) {
                // Update the oldEntries
                new Thread(new Runnable() {
                    public void run() {

                        ArrayList<Task> allTasks = new ArrayList<>();
                        allTasks.addAll(user.getDailys());
                        allTasks.addAll(user.getTodos());
                        allTasks.addAll(user.getHabits());
                        allTasks.addAll(user.getRewards());

                        loadAndRemoveOldTasks(user.getId(), allTasks);

                        ArrayList<ChecklistItem> allChecklistItems = new ArrayList<>();

                        for(Task t : allTasks){
                            if(t.checklist != null) {
                                allChecklistItems.addAll(t.checklist);
                            }
                        }

                        loadAndRemoveOldChecklists(allChecklistItems);
                    }
                }).start();
            }
        }
    }

    private void loadAndRemoveOldTasks(String userId, final List<Task> onlineEntries) {
        final ArrayList<String> onlineTaskIdList = new ArrayList<>();

        for (Task oTask : onlineEntries) {
            onlineTaskIdList.add(oTask.getId());
        }

        // Load Database Tasks
        new Select().from(Task.class).where(Condition.column("user_id").eq(userId)).async().queryList(new TransactionListener<List<Task>>() {
            @Override
            public void onResultReceived(List<Task> tasks) {

                ArrayList<Task> tasksToDelete = new ArrayList<Task>();

                for (Task dbTask : tasks) {
                    if (!onlineTaskIdList.contains(dbTask.getId())) {
                        tasksToDelete.add(dbTask);
                    }
                }

                for (Task delTask : tasksToDelete) {
                    // TaskTag
                    new Delete().from(TaskTag.class).where(Condition.column("task_id").eq(delTask.getId())).async().execute();

                    // ChecklistItem
                    new Delete().from(ChecklistItem.class).where(Condition.column("task_id").eq(delTask.getId())).async().execute();

                    // Days
                    new Delete().from(Days.class).where(Condition.column("task_id").eq(delTask.getId())).async().execute();

                    // TASK
                    delTask.async().delete();

                    EventBus.getDefault().post(new OldTaskRemovedEvent(delTask.getId()));
                }
            }

            @Override
            public boolean onReady(BaseTransaction<List<Task>> baseTransaction) {
                return false;
            }

            @Override
            public boolean hasResult(BaseTransaction<List<Task>> baseTransaction, List<Task> tasks) {
                return tasks != null && tasks.size() > 0;
            }
        });
    }

    private void loadAndRemoveOldChecklists(final List<ChecklistItem> onlineEntries){
        final ArrayList<String> onlineChecklistItemIdList = new ArrayList<>();

        for (ChecklistItem item : onlineEntries) {
            onlineChecklistItemIdList.add(item.getId());
        }

        // Load Database Tasks
        new Select().from(ChecklistItem.class).async().queryList(new TransactionListener<List<ChecklistItem>>() {
            @Override
            public void onResultReceived(List<ChecklistItem> items) {

                ArrayList<ChecklistItem> checkListItemsToDelete = new ArrayList<>();

                for (ChecklistItem chItem : items) {
                    if (!onlineChecklistItemIdList.contains(chItem.getId())) {
                        checkListItemsToDelete.add(chItem);
                    }
                }

                for (ChecklistItem chItem : checkListItemsToDelete) {
                   chItem.async().delete();
                }
            }

            @Override
            public boolean onReady(BaseTransaction<List<ChecklistItem>> baseTransaction) {
                return false;
            }

            @Override
            public boolean hasResult(BaseTransaction<List<ChecklistItem>> baseTransaction, List<ChecklistItem> items) {
                return items != null && items.size() > 0;
            }
        });
    }

    private void updateUserAvatars() {
        avatarInHeader.updateData(user);
    }

    private void updateHeader() {
        updateUserAvatars();
        setTitle(user.getProfile().getName());

        android.support.v7.app.ActionBarDrawerToggle actionBarDrawerToggle = drawer.getActionBarDrawerToggle();

        if (actionBarDrawerToggle != null) {
            actionBarDrawerToggle.setDrawerIndicatorEnabled(true);
        }
    }

    public void updateSidebar() {
        final IProfile profile = accountHeader.getProfiles().get(0);
        if (user.getAuthentication() != null) {
            if (user.getAuthentication().getLocalAuthentication() != null) {
                profile.withEmail(user.getAuthentication().getLocalAuthentication().getEmail());
            }
        }
        profile.withName(user.getProfile().getName());
        new UserPicture(user, this, true, false).setPictureWithRunnable(new UserPictureRunnable() {
            public void run(Bitmap avatar) {
                profile.withIcon(avatar);
                accountHeader.updateProfile(profile);
            }
        });
        accountHeader.updateProfile(profile);
    }

    @Override
    public void onUserReceived(HabitRPGUser user) {
        this.user = user;
        MainActivity.this.setUserData(false);
    }

    @Override
    public void onUserFail() {
    }

    public void setActiveFragment(BaseFragment fragment) {
        this.activeFragment = fragment;
        this.drawer.setSelectionAtPosition(this.activeFragment.fragmentSidebarPosition, false);
    }

    public void onBackPressed() {
        if (drawer.isDrawerOpen()) {
            drawer.closeDrawer();
        } else {
            super.onBackPressed();
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        checkout.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onDestroy() {
        if (checkout != null)
            checkout.stop();

        super.onDestroy();
    }

    public void showSnackbar(String content) {
        showSnackbar(content, SnackbarDisplayType.NORMAL);
    }

    public void showSnackbar(String content, SnackbarDisplayType displayType) {
        Snackbar snackbar = Snackbar.make(floatingMenuWrapper, content, Snackbar.LENGTH_LONG);
        View snackbarView = snackbar.getView();

        if (displayType == SnackbarDisplayType.FAILURE) {

            //change Snackbar's background color;
            snackbarView.setBackgroundColor(ContextCompat.getColor(this, R.color.worse_10));
        } else if (displayType == SnackbarDisplayType.DROP) {
            TextView tv = (TextView) snackbarView.findViewById(android.support.design.R.id.snackbar_text);
            tv.setMaxLines(5);
            snackbarView.setBackgroundColor(ContextCompat.getColor(this, R.color.best_10));
        }
        snackbar.show();
    }
}
