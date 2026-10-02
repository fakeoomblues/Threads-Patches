package com.zeldrisho.threads.extension;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, instrumentedPackages = "com.zeldrisho.threads.extension")
public class OpenLinksExternallyTest {
  @Test
  public void validWebUrlLaunchesExternalActivity() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    Intent query = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/path"));
    Shadows.shadowOf(activity.getPackageManager()).addResolveInfoForIntent(query, browser());

    assertTrue(OpenLinksExternally.open(activity, " https://example.com/path "));
    Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
    assertNotNull(started);
    assertEquals(Intent.ACTION_VIEW, started.getAction());
    assertEquals(Uri.parse("https://example.com/path"), started.getData());
  }

  @Test
  public void invalidUrlsAndMissingExternalHandlerFallBack() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    assertFalse(OpenLinksExternally.open(null, "https://example.com"));
    assertFalse(OpenLinksExternally.open(activity, null));
    assertFalse(OpenLinksExternally.open(activity, "javascript:alert(1)"));
    assertFalse(OpenLinksExternally.open(activity, "https:///missing-host"));
    assertFalse(OpenLinksExternally.open(activity, "not a URL"));
    assertFalse(OpenLinksExternally.open(activity, "https://example.com"));
    assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
  }

  @Test
  public void doesNotRecaptureUrlsResolvedToThreadsItself() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    Intent query = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"));
    ResolveInfo threads = browser();
    threads.activityInfo.packageName = activity.getPackageName();
    Shadows.shadowOf(activity.getPackageManager()).addResolveInfoForIntent(query, threads);

    assertFalse(OpenLinksExternally.open(activity, "https://example.com"));
    assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
  }

  @Test
  public void applicationContextUsesNewTaskFlag() {
    android.content.Context context = RuntimeEnvironment.getApplication();
    Intent query = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"));
    Shadows.shadowOf(context.getPackageManager()).addResolveInfoForIntent(query, browser());

    assertTrue(OpenLinksExternally.open(context, "https://example.com"));
    Intent started = Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
    assertNotNull(started);
    assertTrue((started.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
  }

  private static ResolveInfo browser() {
    ResolveInfo info = new ResolveInfo();
    info.activityInfo = new ActivityInfo();
    info.activityInfo.packageName = "test.external.browser";
    info.activityInfo.name = "BrowserActivity";
    return info;
  }
}
