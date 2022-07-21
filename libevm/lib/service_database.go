package lib

import (
	"github.com/ethereum/go-ethereum/core/rawdb"
	"github.com/ethereum/go-ethereum/core/state"
	"github.com/ethereum/go-ethereum/ethdb"
	"github.com/ethereum/go-ethereum/log"
)

type Database struct {
	storage  ethdb.Database
	database state.Database
}

type DatabaseParams struct {
	DatabaseHandle int `json:"databaseHandle"`
}

type LevelDBParams struct {
	Path string `json:"path"`
}

func (s *Service) open(storage ethdb.Database) int {
	db := &Database{
		storage:  storage,
		database: state.NewDatabase(storage),
		// TODO: enable caching
		//database: state.NewDatabaseWithConfig(storage, &trie.Config{Cache: 16})
	}
	return s.databases.Add(db)
}

func (s *Service) OpenMemoryDB() (error, int) {
	log.Info("initializing memorydb")
	return nil, s.open(rawdb.NewMemoryDatabase())
}

func (s *Service) OpenLevelDB(params LevelDBParams) (error, int) {
	log.Info("initializing leveldb", "path", params.Path)
	storage, err := rawdb.NewLevelDBDatabase(params.Path, 0, 0, "zen/db/data/", false)
	if err != nil {
		log.Error("failed to initialize database", "error", err)
		return err, 0
	}
	return nil, s.open(storage)
}

func (s *Service) CloseDatabase(params DatabaseParams) error {
	err, db := s.databases.Get(params.DatabaseHandle)
	if err != nil {
		return err
	}
	err = db.storage.Close()
	if err != nil {
		log.Error("failed to close storage", "error", err)
	}
	s.databases.Remove(params.DatabaseHandle)
	return err
}
