local component = require("component")
local computer = require("computer")
local event = require("event")
local term = require("term")
local process = require("process")

-- this should be the init level process
process.info().data.window = term.internal.open()

event.listen("gpu_bound", function(ename, gpu)
  gpu=component.proxy(gpu)
  term.bind(gpu)
  computer.pushSignal("term_available")
end)

local function components_changed(ename, address, type)
  local window = term.internal.window()
  if not window then
    return
  end

  if ename == "component_available" or ename == "component_unavailable" then
    type = address
  end

  if ename == "component_removed" or ename == "component_unavailable" then
     -- address can be type, when ename is *_unavailable, but *_removed works here and that's all we need
    if type == "gpu" and window.gpu.address == address then
      window.gpu = nil
      window.keyboard = nil
    elseif type == "keyboard" then
      -- we could check if this was our keyboard
      -- i.e. if address == window.keyboard
      -- but it is also simple for the terminal to
      -- recheck what kb to use
      window.keyboard = nil
    end
  elseif (ename == "component_added" or ename == "component_available") and type == "keyboard" then
  -- we need to clear the current terminals cached keyboard (if any) when
  -- a new keyboard becomes available. This is in case the new keyboard was
  -- attached to the terminal's window. The terminal library has the code to
  -- determine what the best keyboard to use is, but here we'll just set the
  -- cache to nil to force term library to reload it. An alternative to this
  -- method would be to make sure the terminal library doesn't cache the
  -- wrong keybaord to begin with but, users may actually expect that any
  -- primary keyboard is a valid keyboard (weird, in my opinion)
    window.keyboard = nil
  end

  if (type == "screen" or type == "gpu") and not term.isAvailable() then
    computer.pushSignal("term_unavailable")
  end
end

event.listen("component_removed",     components_changed)
event.listen("component_added",       components_changed)
event.listen("component_available",   components_changed)
event.listen("component_unavailable", components_changed)

event.listen("screen_resized", function(_,addr,w,h)
  local window = term.internal.window()
  if term.isAvailable(window) and term.screen(window) == addr and window.fullscreen then
    window.w,window.h = w,h
  end
end)