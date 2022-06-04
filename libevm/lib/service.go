package lib

import (
	"github.com/ethereum/go-ethereum/core/rawdb"
	"github.com/ethereum/go-ethereum/core/state"
	"github.com/ethereum/go-ethereum/ethdb"
	"github.com/ethereum/go-ethereum/log"
)

type Service struct {
	initialized bool
	storage     ethdb.Database
	database    state.Database
	statedbs    map[int]*state.StateDB
	stateHandle int
}

type LevelDBParams struct {
	Path string `json:"path"`
}

func New() *Service {
	return &Service{
		initialized: false,
	}
}

func (s *Service) open(storage ethdb.Database) {
	s.storage = storage
	s.database = state.NewDatabase(storage)
	// TODO: enable caching
	//s.database = state.NewDatabaseWithConfig(storage, &trie.Config{Cache: 16})
	s.statedbs = make(map[int]*state.StateDB)
	s.initialized = true
}

func (s *Service) OpenMemoryDB() error {
	if s.initialized {
		_ = s.CloseDatabase()
	}
	log.Info("initializing memorydb")
	s.open(rawdb.NewMemoryDatabase())
	return nil
}

func (s *Service) OpenLevelDB(params LevelDBParams) error {
	if s.initialized {
		_ = s.CloseDatabase()
	}
	log.Info("initializing leveldb", "path", params.Path)
	storage, err := rawdb.NewLevelDBDatabase(params.Path, 0, 0, "zen/db/data/", false)
	if err != nil {
		log.Error("failed to initialize database", "error", err)
		return err
	}
	s.open(storage)
	return nil
}

func (s *Service) CloseDatabase() error {
	err := s.storage.Close()
	if err != nil {
		log.Error("failed to close storage", "error", err)
	}
	s.initialized = false
	s.statedbs = nil
	s.database = nil
	s.storage = nil
	return err
}
