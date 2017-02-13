/*
 * Copyright (C) 2016 Ordnance Survey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.os.vt.filesystem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.junit.AfterClass;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import rx.Observable;
import rx.Single;
import rx.observers.TestSubscriber;

import uk.os.vt.Entry;
import uk.os.vt.Metadata;

public class StorageImplTest {

  private static final String SINGLE_ZOOM_LEVEL_FILESYSTEM = "z5";
  private static final String MULTIPLE_ZOOM_LEVEL_FILESYSTEM = "z0to4";

  @Test
  public void testGenerateDefaultIsNullWhenNoTiles() throws IOException, JSONException {
    final File file = provideNonExistentTestDirectoryOrBlow();
    boolean isSuccess = file.mkdirs();
    if (!isSuccess) {
      throw new IOException("could not create directory");
    }

    final StorageImpl storage = new StorageImpl.Builder(file).build();

    final Metadata expected = new Metadata.Builder().build();
    final Metadata actual = storage.generateDefault().toBlocking().value();
    JSONAssert.assertEquals(expected.getTileJson(), actual.getTileJson(), false);
  }

  @Test
  public void testGenerateDefaultOnCannedSingleLevel() throws IOException {
    final StorageImpl storage = getStorage(SINGLE_ZOOM_LEVEL_FILESYSTEM);
    final Metadata metaData = storage.generateDefault().toBlocking().value();

    final int actualdMax = metaData.getMaxZoom();
    final int expectedMax = 5;
    assertEquals(expectedMax, actualdMax);

    final int actualMin = metaData.getMinZoom();
    final int expectedMin = 5;
    assertEquals(expectedMin, actualMin);
  }

  @Test
  public void testGenerateDefaultOnCannedMultiLevel() throws IOException {
    final StorageImpl storage = getStorage(MULTIPLE_ZOOM_LEVEL_FILESYSTEM);
    final Metadata metaData = storage.generateDefault().toBlocking().value();

    final int actualdMax = metaData.getMaxZoom();
    final int expectedMax = 4;
    assertEquals(expectedMax, actualdMax);

    final int actualMin = metaData.getMinZoom();
    final int expectedMin = 0;
    assertEquals(expectedMin, actualMin);
  }

  private static StorageImpl getStorage(String directory) throws IOException {
    final File file = getFile(directory);
    return new StorageImpl.Builder(file).build();
  }

  private static File getFile(String file) throws IOException {
    final URL result = StorageImplTest.class.getClassLoader().getResource(file);
    if (result == null) {
      throw new IllegalStateException("Problem accessing resources!");
    }
    try {
      final Path path = Paths.get(result.toURI());
      return path.toFile();
    } catch (final Exception ex) {
      throw new IOException("cannot return resource: " + file, ex);
    }
  }

  @Test
  public void testGeneration() throws IOException, InterruptedException, JSONException {
    String attribution = "Proudly sponsored by Skippy";
    String key = "animal";
    String value = "kangaroo";

    File file = provideNonExistentTestDirectoryOrBlow();
    if (!file.mkdirs()) {
      throw new IOException("cannot create directory");
    }
    final StorageImpl storage = new StorageImpl.Builder(file).build();
    Metadata metaData = storage.generateDefault().toBlocking().value();
    metaData = new Metadata.Builder().copyMetadata(metaData)
        .setAttribution(attribution).setString(key, value).build();

    storage.putMetadata(Single.just(metaData));
    Thread.sleep(1000);
    metaData = storage.getMetadata().toBlocking().first();
    assertNotNull(metaData);

    String actual = metaData.getAttribution();
    String expected = attribution;
    assertEquals(expected, actual);

    String actualValue = metaData.getTileJson().getString(key);
    String expectedValue = value;
    assertEquals(expectedValue, actualValue);
  }

  @Test(expected = IOException.class)
  public void testWhenNoSuchFileDefaultBehaviourIsToBlowUp() throws IOException {
    final File file = provideNonExistentTestDirectoryOrBlow();
    new StorageImpl.Builder(file).build();
  }

  @Test
  public void testWhenNoSuchFileAndInitialisationFlagTrueThenAllOk() throws IOException {
    final File file = provideNonExistentTestDirectoryOrBlow();
    new StorageImpl.Builder(file).createIfNotExist().build();
  }

  @Test
  public void ensureStorageAppearsToBeGood() throws IOException {
    final File file = provideNonExistentTestDirectoryOrBlow();
    final StorageImpl storage = new StorageImpl.Builder(file).createIfNotExist().build();

    final TestSubscriber<Entry> entrySubscriber = new TestSubscriber<>();
    storage.getEntries().subscribe(entrySubscriber);
    entrySubscriber.assertNoErrors();
    entrySubscriber.assertCompleted();

    final TestSubscriber<Metadata> metadataSubscriber = new TestSubscriber<>();
    storage.getMetadata().subscribe(metadataSubscriber);
    metadataSubscriber.assertNoErrors();
    metadataSubscriber.assertCompleted();
  }

  @Test
  public void getEntriesForNonExistentZoomLevel() throws IOException {
    final File file = provideNonExistentTestDirectoryOrBlow();
    final StorageImpl storage = new StorageImpl.Builder(file).createIfNotExist().build();

    final TestSubscriber<Entry> entrySubscriber = new TestSubscriber<>();
    storage.getEntries(22).subscribe(entrySubscriber);
    entrySubscriber.assertNoErrors();
    entrySubscriber.assertCompleted();
  }

  @Test
  public void getMaxZoomEntryForNonExistentZoomLevel() throws IOException {
    final File file = provideNonExistentTestDirectoryOrBlow();
    final StorageImpl storage = new StorageImpl.Builder(file).createIfNotExist().build();

    final TestSubscriber<Integer> entrySubscriber = new TestSubscriber<>();
    storage.getMaxZoomLevel().subscribe(entrySubscriber);
    entrySubscriber.assertNoErrors();
    entrySubscriber.assertCompleted();
  }

  @Test
  public void addAnEntry() throws IOException {
    final File file = provideNonExistentTestDirectoryOrBlow();
    final StorageImpl storage = new StorageImpl.Builder(file).createIfNotExist().build();

    final int zoomLevel = 3;
    final int column = 4;
    final int row = 5;
    final byte[] bytes = getGarbageBytes();

    final Entry in = new Entry(zoomLevel, column, row, bytes);
    storage.putEntries(Observable.just(in));
    final Entry out = storage.getEntries().toBlocking().first();

    assertEquals(in, out);
  }

  @AfterClass
  public static void cleanup() {
    provideNonExistentTestDirectoryOrBlow();
  }

  private static File provideNonExistentTestDirectoryOrBlow() {
    return provideNonExistentTestDirectoryOrBlow("testing");
  }

  private static File provideNonExistentTestDirectoryOrBlow(String filename) {
    final File file = new File(filename);
    if (file.exists()) {
      try {
        FileUtils.deleteDirectory(file);
      } catch (final IOException ex) {
        throw new IllegalStateException(
            "problem with integration test environment - dirty filesystem", ex);
      }
    }
    return file;
  }

  private byte[] getGarbageBytes() {
    try {
      return "test".getBytes("UTF-8");
    } catch (UnsupportedEncodingException uee) {
      return new byte[]{};
    }
  }
}
