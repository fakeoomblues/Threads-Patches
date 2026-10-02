package com.zeldrisho.threads.extension;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.Test;

/** ABI contract used by the injected Threads invoke-static call. */
public class ExtensionContractTest {
  @Test
  public void filterAdsHasExactInjectedAbi() throws Exception {
    Method method =
        FeedAdFilter.class.getDeclaredMethod(
            "filterAds",
            List.class,
            String.class,
            String.class,
            String.class,
            String.class,
            String.class);
    assertTrue(Modifier.isPublic(method.getModifiers()));
    assertTrue(Modifier.isStatic(method.getModifiers()));
    assertEquals(List.class, method.getReturnType());
  }

  @Test
  public void openLinksHasExactInjectedAbi() throws Exception {
    Method method =
        OpenLinksExternally.class.getDeclaredMethod(
            "open", android.content.Context.class, String.class);
    assertTrue(Modifier.isPublic(method.getModifiers()));
    assertTrue(Modifier.isStatic(method.getModifiers()));
    assertEquals(boolean.class, method.getReturnType());
  }
}
