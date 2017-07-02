#!/bin/bash

set -ex

# Only deploy for private repository builds.
if [[ $CIRCLE_PROJECT_REPONAME != 'frontend-private' ]] && [[ "$CIRCLE_REPOSITORY_URL" != *"circleci/frontend-private"* ]]
then
  exit 0
fi

# Git Configuration
export GIT_SSH="$PWD/script/git-ssh-wrap.sh"
echo -e "Host github.com\n\tStrictHostKeyChecking no\n" >> ~/.ssh/config

# Distribute built public files and publish the sha1 of this branch.
tar -cz resources/public/ | aws s3 cp - s3://$DEPLOY_S3_BUCKET/dist/$CIRCLE_SHA1.tgz
echo $CIRCLE_SHA1 | aws s3 cp - s3://$DEPLOY_S3_BUCKET/branch/$CIRCLE_BRANCH

# Keep open source master branch up to date.
export KEYPATH="$HOME/.ssh/id_frontend"
if [[ ! -e $KEYPATH ]]
then
	export KEYPATH="$HOME/.ssh/id_rsa_d992defa668e71b85c150df00c1a1991"
fi

public_repo=git@github.com:circleci/frontend
if [[ $CIRCLE_BRANCH = master ]]; then
  git push $public_repo
fi

# Trigger a build on circleci/circle with latest assets

# Check for matching branch name on backend or create one from production branch.
export KEYPATH="$HOME/.ssh/id_frontend-private"
if [[ ! -e $KEYPATH ]]
then
	export KEYPATH="$HOME/.ssh/id_rsa_92c84680aa0305b33d2c548ac97abe4d"
fi

backend_repo=git@github.com:circleci/circle
heads_file=$(mktemp)
git ls-remote --heads $backend_repo > $heads_file

# backend_branch may be overridden by the next block
backend_branch=$CIRCLE_BRANCH

if ! grep -e "refs/heads/${CIRCLE_BRANCH}$" $heads_file ; then
  # Create a new branch if one doesn't exist
  backend_dir=$HOME/checkouts/circle
  git clone $backend_repo $backend_dir
  # the z is so that it gets pushed to the end of the branch list
  backend_branch="zfe/$CIRCLE_BRANCH"
  git -C $backend_dir branch $backend_branch origin/production
  git -C $backend_dir checkout $backend_branch
  git config --global user.email "frank@circleci.com"
  git config --global user.name "Frank Wang"
  git -C $backend_dir commit --allow-empty -m "[ci skip] trigger zfe build"
  if ! grep -e "refs/heads/${backend_branch}" $heads_file ; then
      git -C $backend_dir push origin $backend_branch:$backend_branch
  else
      # if the branch doesn't exist on the remote, --force-with-lease doesn't work
      git -C $backend_dir push --force-with-lease origin $backend_branch:$backend_branch
  fi
fi

# Trigger a backend build of this sha1.
circle_api=https://circleci.com/api/v1
tree_url=$circle_api/project/circleci/circle/tree/$backend_branch
# BUILD_JSON_PATH should be set in ci config
http_status=$(curl -o "$BUILD_JSON_PATH" \
                   --silent \
                   --write-out '%{http_code}\n' \
                   --header "Content-Type: application/json" \
                   --header "Accept: application/json" \
                   --data "{\"build_parameters\": {\"FRONTEND_DEPLOY_SHA1\": \"$CIRCLE_SHA1\"}}" \
                   --request POST \
                   -L $tree_url?circle-token=$BACKEND_CIRCLE_TOKEN)
[[ 201 = $http_status ]]
