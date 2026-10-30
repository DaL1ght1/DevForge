package artifacts

import (
	"archive/zip"
	"bytes"
	"container/list"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io/fs"
	"regexp"
	"sync"
)

var digestPattern = regexp.MustCompile(`^[0-9a-f]{64}$`)

type Fetcher func(ctx context.Context, digest string) ([]byte, error)

type Store struct {
	fetch      Fetcher
	maxEntries int

	mu       sync.Mutex
	entries  map[string]*list.Element
	order    *list.List
	inflight map[string]*call
}

type entry struct {
	digest string
	fsys   fs.FS
}

type call struct {
	done chan struct{}
	fsys fs.FS
	err  error
}

func NewStore(fetch Fetcher, maxEntries int) *Store {
	if maxEntries < 1 {
		maxEntries = 1
	}
	return &Store{
		fetch:      fetch,
		maxEntries: maxEntries,
		entries:    map[string]*list.Element{},
		order:      list.New(),
		inflight:   map[string]*call{},
	}
}

func (s *Store) Get(ctx context.Context, digest string) (fs.FS, error) {
	if !digestPattern.MatchString(digest) {
		return nil, fmt.Errorf("invalid artifact digest %q", digest)
	}
	s.mu.Lock()
	if el, ok := s.entries[digest]; ok {
		s.order.MoveToFront(el)
		s.mu.Unlock()
		return el.Value.(*entry).fsys, nil
	}
	if c, ok := s.inflight[digest]; ok {
		s.mu.Unlock()
		select {
		case <-c.done:
			return c.fsys, c.err
		case <-ctx.Done():
			return nil, ctx.Err()
		}
	}
	c := &call{done: make(chan struct{})}
	s.inflight[digest] = c
	s.mu.Unlock()

	c.fsys, c.err = s.load(ctx, digest)

	s.mu.Lock()
	delete(s.inflight, digest)
	if c.err == nil {
		s.entries[digest] = s.order.PushFront(&entry{digest: digest, fsys: c.fsys})
		for s.order.Len() > s.maxEntries {
			oldest := s.order.Back()
			s.order.Remove(oldest)
			delete(s.entries, oldest.Value.(*entry).digest)
		}
	}
	s.mu.Unlock()
	close(c.done)
	return c.fsys, c.err
}

func (s *Store) load(ctx context.Context, digest string) (fs.FS, error) {
	data, err := s.fetch(ctx, digest)
	if err != nil {
		return nil, err
	}
	sum := sha256.Sum256(data)
	if hex.EncodeToString(sum[:]) != digest {
		return nil, fmt.Errorf("template artifact %s failed digest verification", digest)
	}
	reader, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		return nil, fmt.Errorf("template artifact %s is not a valid zip: %w", digest, err)
	}
	return reader, nil
}
