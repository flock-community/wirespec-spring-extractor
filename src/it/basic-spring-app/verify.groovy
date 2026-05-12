def wsDir = new File(basedir, "target/wirespec")
assert wsDir.isDirectory(), "wirespec output dir missing"

def files = wsDir.listFiles().collect { it.name }.sort()
assert files == ["UserController.ws", "types.ws"], "unexpected files: $files"

def controller = new File(wsDir, "UserController.ws").text
assert controller.contains("endpoint GetUser GET /users/{id"), "GetUser endpoint missing or wrong path: \n$controller"
assert controller.contains("endpoint CreateUser POST"),         "CreateUser endpoint missing: \n$controller"

def types = new File(wsDir, "types.ws").text
assert types.contains("type UserDto"), "UserDto type missing: \n$types"
assert types.contains("Role"),         "Role enum missing: \n$types"

return true
