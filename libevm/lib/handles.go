package lib

import (
	"errors"
	"fmt"
	"math"
)

var ErrInvalidHandle = errors.New("invalid handle")

type Handles[T comparable] struct {
	used    map[int]T
	current int
}

func NewHandles[T comparable]() *Handles[T] {
	return &Handles[T]{
		used:    make(map[int]T),
		current: 0,
	}
}

func (h *Handles[T]) Add(obj T) int {
	for {
		// wrap around
		if h.current == math.MaxInt32 {
			h.current = 0
		}
		// find the next unused handle:
		// this will never give a handle of 0, which is on purpose - we might consider a handle of 0 as invalid
		h.current++
		if _, exists := h.used[h.current]; !exists {
			h.used[h.current] = obj
			return h.current
		}
	}
}

func (h *Handles[T]) Get(handle int) (error, T) {
	if obj, exists := h.used[handle]; exists {
		return nil, obj
	} else {
		// this gives the default value of type T, i.e. 0 for numbers, false for bool, nil for pointer types, maps, etc.
		empty := *new(T)
		return fmt.Errorf("%w: %d", ErrInvalidHandle, handle), empty
	}
}

func (h *Handles[T]) Remove(handle int) {
	delete(h.used, handle)
}
