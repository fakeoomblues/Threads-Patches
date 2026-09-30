package com.zeldrisho.threads.extension;

import java.util.Arrays;
import java.util.List;

/** Synthetic host shapes used by the feed-filter reflection tests. */
final class FeedAdFilterFixtures {
  private FeedAdFilterFixtures() {}

  /** Fake Instagram Media object with a DED() ad flag. */
  public static class FakeMedia {
    private final boolean ad;

    FakeMedia(boolean ad) {
      this.ad = ad;
    }

    public boolean DED() {
      return ad;
    }
  }

  /** Fake feed unit (LX/3oS) with an A05() accessor for Media. */
  public static class FakeFeedUnit {
    private final FakeMedia media;

    FakeFeedUnit(boolean ad) {
      this.media = new FakeMedia(ad);
    }

    public FakeMedia A05() {
      return media;
    }
  }

  /** Fake thread item with a CDh() accessor for its Media. */
  public static class FakeThreadItem {
    private final FakeMedia media;

    FakeThreadItem(boolean ad) {
      this.media = new FakeMedia(ad);
    }

    public FakeMedia CDh() {
      return media;
    }
  }

  /** Fake thread object with a Ckh() accessor for its item list. */
  public static class FakeThread {
    private final List<FakeThreadItem> items;

    FakeThread(FakeThreadItem... items) {
      this.items = Arrays.asList(items);
    }

    public List<FakeThreadItem> Ckh() {
      return items;
    }
  }

  /** Fake feed unit wrapping a thread via A02() accessor. */
  public static class FakeThreadUnit {
    private final FakeThread thread;

    FakeThreadUnit(FakeThread thread) {
      this.thread = thread;
    }

    public FakeThread A02() {
      return thread;
    }
  }

  /** Fake ad header (X/1qQ on 434) that directly exposes DED() as true. */
  public static class FakeAdHeader {
    public boolean DED() {
      return true;
    }
  }

  /** Fake Instagram Media object (445) with a DGK() ad flag. */
  public static class FakeMedia445 {
    private final boolean ad;

    /** Creates media with the requested ad status. */
    FakeMedia445(boolean ad) {
      this.ad = ad;
    }

    /** Returns the 445 ad predicate value. */
    public boolean DGK() {
      return ad;
    }
  }

  /** Fake feed unit (LX/0hJ on 445) with an A05() accessor for Media. */
  public static class FakeFeedUnit445 {
    private final FakeMedia445 media;

    /** Creates a feed unit whose media has the requested ad status. */
    FakeFeedUnit445(boolean ad) {
      this.media = new FakeMedia445(ad);
    }

    /** Returns the media carried by this feed unit. */
    public FakeMedia445 A05() {
      return media;
    }
  }

  /** Fake thread item (445) with a CIV() accessor for its Media. */
  public static class FakeThreadItem445 {
    private final FakeMedia445 media;

    /** Creates a thread item whose media has the requested ad status. */
    FakeThreadItem445(boolean ad) {
      this.media = new FakeMedia445(ad);
    }

    /** Returns the media carried by this thread item. */
    public FakeMedia445 CIV() {
      return media;
    }
  }

  /** Fake thread object (445) with a Cnd() accessor for its item list. */
  public static class FakeThread445 {
    private final List<FakeThreadItem445> items;

    /** Creates a thread containing the supplied items. */
    FakeThread445(FakeThreadItem445... items) {
      this.items = Arrays.asList(items);
    }

    /** Returns the items carried by this thread. */
    public List<FakeThreadItem445> Cnd() {
      return items;
    }
  }

  /** Fake feed unit wrapping a 445 thread via A02() accessor. */
  public static class FakeThreadUnit445 {
    private final FakeThread445 thread;

    /** Creates a feed unit carrying the supplied thread. */
    FakeThreadUnit445(FakeThread445 thread) {
      this.thread = thread;
    }

    /** Returns the thread carried by this feed unit. */
    public FakeThread445 A02() {
      return thread;
    }
  }

  /** Fake ad header (X/2xO on 445) that directly exposes DGK() as true. */
  public static class FakeAdHeader445 {
    /** Returns the direct 445 ad predicate. */
    public boolean DGK() {
      return true;
    }
  }
}
