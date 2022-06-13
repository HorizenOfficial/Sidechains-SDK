package lib

import (
	"fmt"
	"math"
)

type Handles struct {
	used    map[int]interface{}
	current int
}

func NewHandles() *Handles {
	return &Handles{
		used:    make(map[int]interface{}),
		current: 0,
	}
}

func (h *Handles) Add(obj interface{}) int {
	// find the next unused handle:
	// this will never give a handle of 0, which is on purpose - we might consider a handle of 0 as invalid
	h.current++
	for h.used[h.current] != nil {
		// wrap around
		if h.current == math.MaxInt32 {
			h.current = 0
		}
		h.current++
	}
	h.used[h.current] = obj
	return h.current
}

func (h *Handles) Get(handle int) (error, interface{}) {
	obj := h.used[handle]
	if obj == nil {
		return fmt.Errorf("invalid handle: %d", handle), nil
	}
	return nil, h.used[handle]
}

func (h *Handles) Remove(handle int) {
	delete(h.used, handle)
}
