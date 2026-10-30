package artifacts

import (
	"archive/zip"
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"io/fs"
	"sync"
	"sync/atomic"
	"testing"
)

func archive(t *testing.T, files map[string]string) ([]byte, string) {
	t.Helper()
	var buf bytes.Buffer
	w := zip.NewWriter(&buf)
	for name, content := range files {
		f, err := w.Create(name)
		if err != nil {
			t.Fatal(err)
		}
		_, _ = f.Write([]byte(content))
	}
	if err := w.Close(); err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(buf.Bytes())
	return buf.Bytes(), hex.EncodeToString(sum[:])
}

func TestGetVerifiesDigestAndServesZipAsFS(t *testing.T) {
	data, digest := archive(t, map[string]string{"base/a.txt": "hello"})
	store := NewStore(func(context.Context, string) ([]byte, error) { return data, nil }, 4)

	fsys, err := store.Get(context.Background(), digest)
	if err != nil {
		t.Fatalf("Get: %v", err)
	}
	content, err := fs.ReadFile(fsys, "base/a.txt")
	if err != nil || string(content) != "hello" {
		t.Fatalf("ReadFile = %q, %v", content, err)
	}
}

func TestGetRejectsTamperedArtifact(t *testing.T) {
	data, digest := archive(t, map[string]string{"a.txt": "x"})
	tampered := append([]byte{}, data...)
	tampered[len(tampered)-1] ^= 0xff
	store := NewStore(func(context.Context, string) ([]byte, error) { return tampered, nil }, 4)

	if _, err := store.Get(context.Background(), digest); err == nil {
		t.Fatal("expected digest verification failure")
	}
}

func TestGetRejectsMalformedDigestWithoutFetching(t *testing.T) {
	var fetched atomic.Int32
	store := NewStore(func(context.Context, string) ([]byte, error) {
		fetched.Add(1)
		return nil, nil
	}, 4)

	if _, err := store.Get(context.Background(), "../../etc/passwd"); err == nil || fetched.Load() != 0 {
		t.Fatalf("expected rejection without fetch, err=%v fetched=%d", err, fetched.Load())
	}
}

func TestConcurrentGetsFetchOnceAndCacheResult(t *testing.T) {
	data, digest := archive(t, map[string]string{"a.txt": "x"})
	var fetched atomic.Int32
	release := make(chan struct{})
	store := NewStore(func(context.Context, string) ([]byte, error) {
		fetched.Add(1)
		<-release
		return data, nil
	}, 4)

	var wg sync.WaitGroup
	for range 20 {
		wg.Add(1)
		go func() {
			defer wg.Done()
			if _, err := store.Get(context.Background(), digest); err != nil {
				t.Errorf("Get: %v", err)
			}
		}()
	}
	close(release)
	wg.Wait()
	if _, err := store.Get(context.Background(), digest); err != nil {
		t.Fatal(err)
	}
	if fetched.Load() != 1 {
		t.Fatalf("fetched %d times, want 1", fetched.Load())
	}
}

func TestFailedFetchIsNotCached(t *testing.T) {
	data, digest := archive(t, map[string]string{"a.txt": "x"})
	var calls atomic.Int32
	store := NewStore(func(context.Context, string) ([]byte, error) {
		if calls.Add(1) == 1 {
			return nil, errors.New("backend down")
		}
		return data, nil
	}, 4)

	if _, err := store.Get(context.Background(), digest); err == nil {
		t.Fatal("expected first fetch to fail")
	}
	if _, err := store.Get(context.Background(), digest); err != nil {
		t.Fatalf("retry: %v", err)
	}
}

func TestLeastRecentlyUsedEntriesAreEvicted(t *testing.T) {
	blobs := map[string][]byte{}
	var digests []string
	for _, name := range []string{"a", "b", "c"} {
		data, digest := archive(t, map[string]string{name: name})
		blobs[digest] = data
		digests = append(digests, digest)
	}
	var calls atomic.Int32
	store := NewStore(func(_ context.Context, d string) ([]byte, error) {
		calls.Add(1)
		return blobs[d], nil
	}, 2)
	ctx := context.Background()
	for _, d := range digests {
		if _, err := store.Get(ctx, d); err != nil {
			t.Fatal(err)
		}
	}
	if _, err := store.Get(ctx, digests[0]); err != nil {
		t.Fatal(err)
	}
	if calls.Load() != 4 {
		t.Fatalf("calls = %d, want 4 (oldest entry evicted and refetched)", calls.Load())
	}
}
