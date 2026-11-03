package main

import (
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	qrcode "github.com/skip2/go-qrcode"
)

const pairingLifetime = 5 * time.Minute
const maxPairingCodes = 32
const deviceTokenPrefix = "olympus-read-v1."

type pairingCodes struct {
	sync.Mutex
	expires map[[32]byte]time.Time
}

func randomPairingValue() (string, error) {
	var nonce [32]byte
	if _, err := rand.Read(nonce[:]); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(nonce[:]), nil
}

func (s *server) signDeviceToken(nonce string) string {
	mac := hmac.New(sha256.New, []byte(s.token))
	mac.Write([]byte(deviceTokenPrefix + nonce))
	return base64.RawURLEncoding.EncodeToString(mac.Sum(nil))
}

func (s *server) readAuthorized(header string) bool {
	if auth(header, s.token) {
		return true
	}
	token, ok := strings.CutPrefix(header, "Bearer "+deviceTokenPrefix)
	if s.token == "" || !ok || len(token) != 87 {
		return false
	}
	nonce, signature, ok := strings.Cut(token, ".")
	return ok && len(nonce) == 43 && hmac.Equal([]byte(signature), []byte(s.signDeviceToken(nonce)))
}

func pairingRequest(w http.ResponseWriter, r *http.Request, value any) bool {
	if r.Method != http.MethodPost {
		w.Header().Set("Allow", http.MethodPost)
		w.WriteHeader(http.StatusMethodNotAllowed)
		return false
	}
	r.Body = http.MaxBytesReader(w, r.Body, 2048)
	if json.NewDecoder(r.Body).Decode(value) != nil {
		write(w, http.StatusBadRequest, map[string]string{"error": "Invalid pairing request"})
		return false
	}
	return true
}

func (s *server) issuePairingCode(w http.ResponseWriter, r *http.Request) {
	if !auth(r.Header.Get("Authorization"), s.token) {
		write(w, http.StatusUnauthorized, map[string]string{"error": "Unauthorized"})
		return
	}
	var request struct{ URL string }
	if !pairingRequest(w, r, &request) {
		return
	}
	address, err := url.Parse(request.URL)
	if err != nil || address.Scheme != "https" || address.Hostname() == "" ||
		address.User != nil || address.Path != "" && address.Path != "/" ||
		address.RawQuery != "" || address.ForceQuery || address.Fragment != "" {
		write(w, http.StatusBadRequest, map[string]string{"error": "Use the HTTPS backend origin"})
		return
	}
	code, err := randomPairingValue()
	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		return
	}
	now := time.Now()
	expiry := now.Add(pairingLifetime)
	payload := map[string]any{
		"version": 1, "url": strings.TrimSuffix(address.String(), "/"), "code": code, "expiresAt": expiry.UTC(),
	}
	var qr []byte
	if r.Header.Get("Accept") == "image/png" {
		data, _ := json.Marshal(payload)
		qr, err = qrcode.Encode(string(data), qrcode.Medium, 384)
		if err != nil {
			w.WriteHeader(http.StatusInternalServerError)
			return
		}
	}
	s.pairing.Lock()
	for key, until := range s.pairing.expires {
		if !until.After(now) {
			delete(s.pairing.expires, key)
		}
	}
	if len(s.pairing.expires) >= maxPairingCodes {
		s.pairing.Unlock()
		write(w, http.StatusTooManyRequests, map[string]string{"error": "Wait for outstanding pairing codes to expire"})
		return
	}
	if s.pairing.expires == nil {
		s.pairing.expires = make(map[[32]byte]time.Time)
	}
	s.pairing.expires[sha256.Sum256([]byte(code))] = expiry
	s.pairing.Unlock()
	if qr != nil {
		w.Header().Set("Content-Type", "image/png")
		w.Header().Set("Cache-Control", "no-store")
		w.WriteHeader(http.StatusCreated)
		_, _ = w.Write(qr)
		return
	}
	write(w, http.StatusCreated, payload)
}

func (s *server) exchangePairingCode(w http.ResponseWriter, r *http.Request) {
	if s.token == "" {
		w.WriteHeader(http.StatusServiceUnavailable)
		return
	}
	var request struct{ Code string }
	if !pairingRequest(w, r, &request) {
		return
	}
	if len(request.Code) != 43 {
		write(w, http.StatusUnauthorized, map[string]string{"error": "Pairing code is invalid or expired"})
		return
	}
	key := sha256.Sum256([]byte(request.Code))
	s.pairing.Lock()
	expiry := s.pairing.expires[key]
	delete(s.pairing.expires, key)
	s.pairing.Unlock()
	if !expiry.After(time.Now()) {
		write(w, http.StatusUnauthorized, map[string]string{"error": "Pairing code is invalid or expired"})
		return
	}
	nonce, err := randomPairingValue()
	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		return
	}
	write(w, http.StatusOK, map[string]string{"token": deviceTokenPrefix + nonce + "." + s.signDeviceToken(nonce)})
}
