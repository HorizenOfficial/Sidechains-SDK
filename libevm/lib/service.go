package lib

// #cgo CFLAGS: -g -Wall -O3 -fpic -Werror
import "C"
import (
	"github.com/ethereum/go-ethereum/core/rawdb"
	"github.com/ethereum/go-ethereum/core/state"
	"github.com/ethereum/go-ethereum/ethdb"
	"github.com/ethereum/go-ethereum/log"
)

type Service struct {
	storage  ethdb.Database
	database state.Database
	statedbs map[int]*state.StateDB
	counter  int
}

func New(storage ethdb.Database) *Service {
	return &Service{
		storage: storage,
		// TODO: enable caching
		//database: state.NewDatabaseWithConfig(storage, &trie.Config{Cache: 16})
		database: state.NewDatabase(storage),
		statedbs: make(map[int]*state.StateDB),
	}
}

func InitWithLevelDB(path string) (*Service, error) {
	log.Info("initializing leveldb", "path", path)
	storage, err := rawdb.NewLevelDBDatabase(path, 0, 0, "zen/db/data/", false)
	if err != nil {
		log.Error("failed to initialize database", "error", err)
		return nil, err
	}
	return New(storage), nil
}

func InitWithMemoryDB() (*Service, error) {
	log.Info("initializing memorydb")
	storage := rawdb.NewMemoryDatabase()
	return New(storage), nil
}

func (s *Service) Close() error {
	err := s.storage.Close()
	if err != nil {
		log.Error("failed to close storage", "error", err)
	}
	return err
}
