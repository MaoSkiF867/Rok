package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.SessionIconKt;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.RunnableData;

public class EditSessionController extends EditBaseController<EditSessionController.Args> implements View.OnClickListener {
  private DoubleHeaderView headerCell;
  private SettingsAdapter adapter;

  private TdApi.Session session;
  private boolean allowSecretChats;
  private boolean allowCalls;

  public EditSessionController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (hasAnyChanges()) {
      showUnsavedChangesPromptBeforeLeaving(null);
      return true;
    }

    return false;
  }

  @Override
  protected boolean swipeNavigationEnabled () {
    return !hasAnyChanges();
  }

  private void checkDoneButton () {
    setDoneVisible(hasAnyChanges());
  }

  private boolean hasAnyChanges () {
    return allowSecretChats != session.canAcceptSecretChats || allowCalls != session.canAcceptCalls;
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.session = args.session;
    this.allowSecretChats = args.session.canAcceptSecretChats;
    this.allowCalls = args.session.canAcceptCalls;
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_sessionAcceptSecretChats:
        this.allowSecretChats = adapter.toggleView(v);
        adapter.updateValuedSettingById(R.id.btn_sessionAcceptSecretChats);
        checkDoneButton();
        break;
      case R.id.btn_sessionAcceptCalls:
        this.allowCalls = adapter.toggleView(v);
        adapter.updateValuedSettingById(R.id.btn_sessionAcceptCalls);
        checkDoneButton();
        break;
      case R.id.btn_sessionLogout:
        showOptions(null, new int[]{R.id.btn_terminateSession, R.id.btn_cancel}, new String[]{Lang.getString(session.isPasswordPending ? R.string.TerminateIncompleteSession : R.string.TerminateSession), Lang.getString(R.string.Cancel)}, new int[]{OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
          if (id == R.id.btn_terminateSession) {
            navigateBack();
            getArgumentsStrict().sessionTerminationListener.run();
          }

          return true;
        });
        break;
    }
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.InviteLinkEdit);
  }

  @Override
  protected boolean onDoneClick () {
    setDoneInProgress(true);

    List<TdApi.Function> functions = new ArrayList<>();

    if (allowSecretChats != session.canAcceptSecretChats) {
      functions.add(new TdApi.ToggleSessionCanAcceptSecretChats(session.id, allowSecretChats));
    }

    if (allowCalls != session.canAcceptCalls) {
      functions.add(new TdApi.ToggleSessionCanAcceptCalls(session.id, allowCalls));
    }

    tdlib.sendAll(functions.toArray(new TdApi.Function[0]), (obj) -> {

    }, () -> {
      runOnUiThreadOptional(this::navigateBack);
    });

    return true;
  }

  @Override
  public int getId () {
    return R.id.controller_editSession;
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    headerCell = new DoubleHeaderView(context());
    headerCell.setThemedTextColor(this);
    headerCell.initWithMargin(Screen.dp(49f), true);
    headerCell.setTitle(R.string.SessionDetails);
    headerCell.setSubtitle(Lang.getReverseRelativeDate(
      session.lastActiveDate, TimeUnit.SECONDS,
      tdlib.currentTimeMillis(), TimeUnit.MILLISECONDS,
      true, 0, R.string.session_LastActive, false
    ));

    setDoneIcon(R.drawable.baseline_check_24);
    setInstantDoneVisible(true);

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_sessionLogout) {
          view.setIconColorId(R.id.theme_color_textNegative);
        } else {
          view.setIconColorId(R.id.theme_color_icon);
        }

        switch (item.getId()) {
          case R.id.btn_sessionDevice:
            view.setText(new TextWrapper(session.deviceModel, TGMessage.simpleTextStyleProvider(), TextColorSets.Regular.NORMAL, null));
            break;
          case R.id.btn_sessionApp:
            view.setText(new TextWrapper(session.applicationName + " " + session.applicationVersion, TGMessage.simpleTextStyleProvider(), TextColorSets.Regular.NORMAL, null));
            break;
          case R.id.btn_sessionPlatform:
            view.setData(R.string.SessionSystem);
            break;
          case R.id.btn_sessionCountry:
            view.setData(R.string.SessionLocation);
            break;
          case R.id.btn_sessionIp:
            view.setName("127.0.0.1");
            view.setData(R.string.SessionIP);
            break;
          case R.id.btn_sessionFirstLogin:
            view.setData(R.string.SessionFirstLogin);
            break;
          case R.id.btn_sessionLastLogin:
            view.setData(R.string.SessionLastLogin);
            break;
          case R.id.btn_sessionLogout:
            view.setData(session.isPasswordPending ? null : Lang.getReverseRelativeDate(
              session.lastActiveDate + TimeUnit.DAYS.toSeconds(getArgumentsStrict().inactiveSessionTtlDays), TimeUnit.SECONDS,
              tdlib.currentTimeMillis(), TimeUnit.MILLISECONDS,
              true, 0, R.string.session_WillTerminate, false
            ));
            break;
          case R.id.btn_sessionAcceptSecretChats:
            view.getToggler().setRadioEnabled(allowSecretChats, isUpdate);
            view.setData(allowSecretChats ? R.string.SessionAccept : R.string.SessionReject);
            break;
          case R.id.btn_sessionAcceptCalls:
            view.getToggler().setRadioEnabled(allowCalls, isUpdate);
            view.setData(allowCalls ? R.string.SessionAccept : R.string.SessionReject);
            break;
        }
      }
    };

    List<ListItem> items = new ArrayList<>();

    items.add(new ListItem(ListItem.TYPE_INFO_MULTILINE, R.id.btn_sessionApp, R.drawable.baseline_apps_24, R.string.SessionApp, false));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_INFO_MULTILINE, R.id.btn_sessionDevice, R.drawable.baseline_devices_other_24, R.string.SessionDevice, false));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_sessionPlatform, SessionIconKt.asIcon(session), (session.platform + " " + session.systemVersion).trim(), false));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_sessionCountry, R.drawable.baseline_location_on_24, session.country, false));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_sessionIp, R.drawable.baseline_language_24, session.ip, false));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    if (!session.isPasswordPending) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.SessionAccepts));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_sessionAcceptSecretChats, R.drawable.baseline_lock_24, R.string.SessionSecretChats));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_sessionAcceptCalls, R.drawable.baseline_call_24, R.string.SessionCalls));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_sessionFirstLogin, R.drawable.baseline_exit_to_app_24, Lang.getTimestamp(session.logInDate, TimeUnit.SECONDS), false));
    if (!session.isPasswordPending) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_sessionLastLogin, R.drawable.baseline_history_24, Lang.getTimestamp(session.lastActiveDate, TimeUnit.SECONDS), false));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(session.isPasswordPending ? ListItem.TYPE_SETTING : ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_sessionLogout, R.drawable.baseline_dangerous_24, session.isPasswordPending ? R.string.TerminateIncompleteSession : R.string.TerminateSession).setTextColorId(R.id.theme_color_textNegative));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    adapter.setItems(items, false);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    recyclerView.setAdapter(adapter);
    checkDoneButton();
  }

  public static class Args {
    public final TdApi.Session session;
    public final int inactiveSessionTtlDays;
    public final Runnable sessionTerminationListener;

    public Args (TdApi.Session session, int inactiveSessionTtlDays, Runnable sessionTerminationListener) {
      this.session = session;
      this.inactiveSessionTtlDays = inactiveSessionTtlDays;
      this.sessionTerminationListener = sessionTerminationListener;
    }
  }

  @Override
  protected int getRecyclerBackgroundColorId () {
    return R.id.theme_color_background;
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }
}
