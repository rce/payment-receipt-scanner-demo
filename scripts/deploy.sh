#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/lib/common-functions.sh"

function main {
  setup_aws "payment-receipt-scanner-demo"
  init_nodejs "18"

  build_web_assets

  cd "$repo/infra"
  npm install
  npx cdk bootstrap "aws://$AWS_ACCOUNT_ID/$AWS_REGION"
  npx cdk deploy --app "npx ts-node ./Infra.ts" --require-approval never Infra
}

function build_web_assets {
  cd "$repo/web"
  npm install
  npm run build
}

main "$@"
