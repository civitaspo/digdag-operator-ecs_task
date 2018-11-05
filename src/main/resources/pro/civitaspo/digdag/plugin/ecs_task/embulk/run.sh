set -ex
set -o pipefail

mkdir -p ./digdag-operator-ecs_task
cd digdag-operator-ecs_task

# Create output files
touch out.json stdout.log stderr.log

# Download requirements
aws s3 cp s3://${ECS_TASK_EMBULK_BUCKET}/${ECS_TASK_EMBULK_PREFIX}/ ./ --recursive

# Move workspace
cd workspace

# Unset e option for returning embulk results to digdag
set +e

# Run setup command
${ECS_TASK_EMBULK_SETUP_COMMAND} \
        2>> ../stderr.log \
    | tee -a ../stdout.log

# Run
embulk run ../config.yml \
        2>> ../stderr.log \
    | tee -a ../stdout.log

# Capture exit code
EXIT_CODE=$?

# Set e option
set -e

# Move out workspace
cd ..

# For logging driver
cat stderr.log 1>&2

# Write out.json
cat <<EOF > out.json
{
  "subtask_config": {},
  "export_params": {},
  "store_params": {},
  "status_params": {
    "exit_code": $EXIT_CODE
  }
}
EOF

# Upload results
aws s3 cp ./out.json s3://${ECS_TASK_EMBULK_BUCKET}/${ECS_TASK_EMBULK_PREFIX}/
aws s3 cp ./stdout.log s3://${ECS_TASK_EMBULK_BUCKET}/${ECS_TASK_EMBULK_PREFIX}/
aws s3 cp ./stderr.log s3://${ECS_TASK_EMBULK_BUCKET}/${ECS_TASK_EMBULK_PREFIX}/

# Exit with the embulk exit code
exit $EXIT_CODE

