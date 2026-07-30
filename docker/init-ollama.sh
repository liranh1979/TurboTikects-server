#!/bin/sh

# Start Ollama daemon in background
ollama serve &

# Wait for Ollama service to respond
until ollama list > /dev/null 2>&1; do
  echo "Waiting for Ollama engine..."
  sleep 1
done

echo "Ollama is running. Checking base model..."
ollama pull gemma4:e2b

# Create custom 8k context model if not present
if ! ollama list | grep -q "gemma4-8k"; then
  echo "Creating custom model 'gemma4-8k' with num_ctx=8192..."
  cat <<EOF > /tmp/Modelfile
FROM gemma4:e2b
PARAMETER num_ctx 8192
PARAMETER temperature 0.0
EOF
  ollama create gemma4-8k -f /tmp/Modelfile
fi

echo "Initialization complete. Ready for Spring Boot connections."

# Keep background daemon running
wait
