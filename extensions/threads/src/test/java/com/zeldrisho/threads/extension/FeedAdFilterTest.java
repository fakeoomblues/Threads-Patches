package com.zeldrisho.threads.extension;

import static com.zeldrisho.threads.extension.FeedAdFilterFixtures.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.junit.Test;

/** Unit tests for {@link FeedAdFilter}. Pure JVM — no Android deps. */
public class FeedAdFilterTest {

  /** Null and empty lists must pass through unchanged. */
  @Test
  public void nullAndEmptyPassthrough() {
    assertEquals(null, FeedAdFilter.filterAds(null));
    List<?> empty = Collections.emptyList();
    assertSame(empty, FeedAdFilter.filterAds(empty));
  }

  /** When no ads are present, the filter must return the same list instance (no copy). */
  @Test
  public void allOrganicReturnsSameInstance() {
    List<Object> in =
        new ArrayList<>(Arrays.asList(new FakeFeedUnit(false), new FakeFeedUnit(false)));
    assertSame(in, FeedAdFilter.filterAds(in));
  }

  @Test
  public void linkedListTraversalRemainsLinearAndPreservesOrder() {
    Object first = new FakeFeedUnit(false);
    Object ad = new FakeFeedUnit(true);
    Object last = new FakeFeedUnit(false);
    List<Object> in = new LinkedList<>(Arrays.asList(first, ad, last));

    List<?> out = FeedAdFilter.filterAds(in);

    assertEquals(2, out.size());
    assertSame(first, out.get(0));
    assertSame(last, out.get(1));
  }

  /** Ad headers with direct DED() must be filtered out. */
  @Test
  public void directDedHeaderRemoved() {
    Object ad = new FakeAdHeader();
    Object post = new FakeFeedUnit(false);
    List<?> out = FeedAdFilter.filterAds(Arrays.asList(ad, post));
    assertEquals(1, out.size());
    assertSame(post, out.get(0));
  }

  /** Feed units with ad media (A05() returns Media with DED=true) must be filtered out. */
  @Test
  public void mediaDedRemoved() {
    Object ad = new FakeFeedUnit(true);
    Object post = new FakeFeedUnit(false);
    List<?> out = FeedAdFilter.filterAds(Arrays.asList(ad, post));
    assertEquals(1, out.size());
    assertSame(post, out.get(0));
  }

  @Test
  public void patchTimeResolvedThreadItemsAccessorIsUsed() {
    FakeThreadUnit adUnit = new FakeThreadUnit(new FakeThread(new FakeThreadItem(true)));
    FakeThreadUnit post = new FakeThreadUnit(new FakeThread(new FakeThreadItem(false)));
    List<?> out = FeedAdFilter.filterAds(Arrays.asList(adUnit, post), "DED", "A05", "A02", "Ckh");
    assertEquals(1, out.size());
    assertSame(post, out.get(0));
  }

  /** Thread units with any ad item in their Ckh()->CDh()->DED() chain must be filtered out. */
  @Test
  public void threadCarriedAdRemoved() {
    FakeThread thread = new FakeThread(new FakeThreadItem(false), new FakeThreadItem(true));
    Object adUnit = new FakeThreadUnit(thread);
    Object post = new FakeFeedUnit(false);
    List<?> out = FeedAdFilter.filterAds(Arrays.asList(adUnit, post));
    assertEquals(1, out.size());
    assertSame(post, out.get(0));
  }

  /** 445 ad headers with direct DGK() must be filtered out. */
  @Test
  public void directDgkHeaderRemoved() {
    FeedAdFilter.clearCacheForTest();
    Object ad = new FakeAdHeader445();
    Object post = new FakeFeedUnit445(false);
    List<?> out = FeedAdFilter.filterAds(Arrays.asList(ad, post));
    assertEquals(1, out.size());
    assertSame(post, out.get(0));
  }

  /** 445 feed units with ad media (A05() returns Media with DGK=true) must be filtered out. */
  @Test
  public void mediaDgkRemoved() {
    FeedAdFilter.clearCacheForTest();
    Object ad = new FakeFeedUnit445(true);
    Object post = new FakeFeedUnit445(false);
    List<?> out = FeedAdFilter.filterAds(Arrays.asList(ad, post));
    assertEquals(1, out.size());
    assertSame(post, out.get(0));
  }

  /** 445 thread units with any ad item in their Cnd()->CIV()->DGK() chain must be filtered out. */
  @Test
  public void threadCarriedAd445Removed() {
    FeedAdFilter.clearCacheForTest();
    FakeThread445 thread =
        new FakeThread445(new FakeThreadItem445(false), new FakeThreadItem445(true));
    Object adUnit = new FakeThreadUnit445(thread);
    Object post = new FakeFeedUnit445(false);
    List<?> out = FeedAdFilter.filterAds(Arrays.asList(adUnit, post));
    assertEquals(1, out.size());
    assertSame(post, out.get(0));
  }

  /** Mixed 434/445 feeds must filter on both shapes in one pass. */
  @Test
  public void mixedVersionFeedRemoved() {
    FeedAdFilter.clearCacheForTest();
    Object ad434 = new FakeFeedUnit(true);
    Object ad445 = new FakeFeedUnit445(true);
    Object post434 = new FakeFeedUnit(false);
    Object post445 = new FakeFeedUnit445(false);
    List<?> out = FeedAdFilter.filterAds(Arrays.asList(ad434, ad445, post434, post445));
    assertEquals(2, out.size());
    assertSame(post434, out.get(0));
    assertSame(post445, out.get(1));
  }

  /** Immutable input lists must be copied (not modified in-place) when filtering. */
  @Test
  public void immutableInputReturnsFilteredCopy() {
    // Regression guard: in-place Iterator.remove() on an immutable list
    // throws UnsupportedOperationException — the filter must copy.
    List<?> in =
        Collections.unmodifiableList(
            new ArrayList<>(Arrays.asList(new FakeFeedUnit(true), new FakeFeedUnit(false))));
    List<?> out = FeedAdFilter.filterAds(in);
    assertEquals(1, out.size());
  }

  /** Unknown object shapes (no DED/A05/A02 methods) must be kept and must not crash. */
  @Test
  public void unknownShapeIsKeptNoCrash() {
    // R8 rename drift: objects without DED/A05/A02 must be kept, never crash.
    Object unknown = new Object();
    List<?> in = Collections.singletonList(unknown);
    assertSame(in, FeedAdFilter.filterAds(in));
  }

  /** Null items in the feed list must be preserved (not filtered). */
  @Test
  public void nullItemsKept() {
    Object post = new FakeFeedUnit(false);
    List<?> out = FeedAdFilter.filterAds(Arrays.asList(null, post));
    assertTrue(out.contains(null));
    assertTrue(out.contains(post));
  }

  /** First use must populate the reflection cache, including misses for unknown shapes. */
  @Test
  public void reflectionCachePopulatesOnFirstUse() {
    FeedAdFilter.clearCacheForTest();
    assertEquals(0, FeedAdFilter.cachedMethodCountForTest());
    List<?> in = new ArrayList<>(Arrays.asList(new FakeFeedUnit(true), new FakeFeedUnit(false)));
    List<?> out = FeedAdFilter.filterAds(in);
    assertEquals(1, out.size());
    assertTrue(FeedAdFilter.cachedMethodCountForTest() > 0);
  }

  /** Repeat filtering must reuse cached lookups (no growth) with identical results. */
  @Test
  public void repeatFilteringReusesCache() {
    FeedAdFilter.clearCacheForTest();
    Object ad = new FakeFeedUnit(true);
    Object post = new FakeFeedUnit(false);
    Object unknown = new Object();
    List<Object> in = new ArrayList<>(Arrays.asList(ad, post, unknown));
    List<?> first = FeedAdFilter.filterAds(in);
    int cached = FeedAdFilter.cachedMethodCountForTest();
    assertTrue(cached > 0);
    List<?> second = FeedAdFilter.filterAds(in);
    assertEquals(first.size(), second.size());
    assertSame(first.get(0), second.get(0));
    assertEquals(cached, FeedAdFilter.cachedMethodCountForTest());
  }
}
