#include "../PetBiteESP32/Rules.h"
#include <cassert>
#include <limits>
int main() {
    using namespace petbite;
    assert(hopperLoss(1000,960) == 40);
    assert(hopperLoss(1000,1010) < 0);
    assert(sequenceAllowed(2,1) && !sequenceAllowed(1,1) && !sequenceAllowed(0,0) && !sequenceAllowed(1,2));
    assert(weightInRange(2000,2000) && !weightInRange(2001,2000));
    assert(!weightInRange(std::numeric_limits<float>::quiet_NaN(),2000));
    assert(withinTolerance(38,40,5) && !withinTolerance(46,40,5));
    assert(!withinTolerance(std::numeric_limits<float>::quiet_NaN(),40,5));
    assert(!due(true,8,0,8,0,20261010,20261009));
    assert(validPortion(5) && validPortion(300) && !validPortion(301) && !validPortion(0));
    assert(validTime(23,59) && !validTime(24,0));
    assert(due(true,8,0,8,0,20261008,20261009));
    assert(!due(true,8,0,8,0,20261009,20261009));
    assert(!due(false,8,0,8,0,0,20261009));
    assert(!due(true,8,0,8,1,0,20261009));
    assert(mustClose(38,40,2,1000,12000));
    assert(mustClose(2,40,2,12000,12000));
    assert(mustClose(-6,40,2,1000,12000));
    assert(mustClose(std::numeric_limits<float>::quiet_NaN(),40,2,1000,12000));
    assert(!mustClose(20,40,2,1000,12000));
}
