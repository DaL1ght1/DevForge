package github

import (
	"fmt"
	"net/http"
	"sync"

	"github.com/bradleyfalzon/ghinstallation/v2"
	"github.com/google/go-github/v92/github"

	"devforge/provisioner/internal/git"
)

type ClientFactory struct {
	appID      int64
	privateKey []byte
	base       http.RoundTripper
	opts       []github.ClientOptionsFunc

	mu         sync.Mutex
	transports map[int64]*ghinstallation.Transport
}

func NewClientFactory(appID int64, privateKey []byte, opts ...github.ClientOptionsFunc) *ClientFactory {
	return &ClientFactory{
		appID:      appID,
		privateKey: privateKey,
		base:       http.DefaultTransport,
		opts:       opts,
		transports: make(map[int64]*ghinstallation.Transport),
	}
}

func (f *ClientFactory) ForInstallation(installationID int64) (*Client, git.TokenSource, error) {
	if installationID <= 0 {
		return nil, nil, fmt.Errorf("invalid installation id %d", installationID)
	}
	transport, err := f.transport(installationID)
	if err != nil {
		return nil, nil, err
	}
	client, err := NewClient(transport, f.opts...)
	if err != nil {
		return nil, nil, err
	}
	return client, transport, nil
}

func (f *ClientFactory) transport(installationID int64) (*ghinstallation.Transport, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if t, ok := f.transports[installationID]; ok {
		return t, nil
	}
	t, err := ghinstallation.New(f.base, f.appID, installationID, f.privateKey)
	if err != nil {
		return nil, fmt.Errorf("github app auth for installation %d failed: %w", installationID, err)
	}
	f.transports[installationID] = t
	return t, nil
}
