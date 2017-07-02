#!/bin/bash

# deploy only happens for private repository builds.
if [[ $CIRCLE_PROJECT_REPONAME != 'frontend-private' ]] && [[ "$CIRCLE_REPOSITORY_URL" != *"circleci/frontend-private"* ]]
then
  exit 0
fi

# BUILD_JSON_PATH should be set in ci config
backend_build_num=$(jq '.build_num' < $BUILD_JSON_PATH)
circle_api=https://circleci.com/api/v1.1
build_url="${circle_api}/project/github/circleci/circle/${backend_build_num}"
RUNNING=true
while [ $RUNNING == true ]; do
    sleep 10;
    status=$(curl --silent --header "Accept: application/json" "${build_url}?circle-token=$BACKEND_CIRCLE_TOKEN" | jq -r '.status');
    echo 'running queued scheduled not_running' | grep --silent "$status" || RUNNING=false;
    echo -n "."
done
echo 'fixed success' | grep --silent "$status"
exit_code=$?
echo ""
echo "Backend build https://circleci.com/gh/circleci/circle/${backend_build_num} finished with status ${status}"

exit $exit_code
